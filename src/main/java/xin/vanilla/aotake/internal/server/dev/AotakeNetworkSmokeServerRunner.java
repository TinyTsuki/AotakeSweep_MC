package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeNotifications;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CommonConfigView;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 在独立服务端内写入并复核网络 smoke 的持久化哨兵。
 */
public final class AotakeNetworkSmokeServerRunner {
    private static final long SPARK_SAMPLE_SECONDS = 60L;
    private static final int SENTINEL_COUNT = 7;
    private static final int SENTINEL_CONFIG_VALUE = 11;
    private static final ItemStack SENTINEL_STACK = new ItemStack(Items.EMERALD, SENTINEL_COUNT);

    private static boolean ready;
    private static boolean commandVerified;
    private static boolean conciseCommandVerified;
    private static boolean finished;
    private static int commandRefreshStage;
    private static int commandRefreshWaitTicks;
    private static int commandRefreshSettleTicks;
    private static int shutdownTicks;
    private static int gameplayTicks;
    private static int chunkWaitTicks;
    private static boolean chunkCandidatesVisible;
    private static GameplayStep gameplayStep = GameplayStep.PREPARE;
    private static ItemEntity countdownItem;
    private static List<ItemEntity> chunkItems = Collections.emptyList();
    private static List<ItemEntity> burstDropItems = Collections.emptyList();
    private static List<ItemEntity> globalSweepItems = Collections.emptyList();
    private static Cow captureTarget;
    private static ReflectiveSparkProfile sparkProfile;
    private static AotakeNetworkSmokeWorkload measuredWorkload;
    private static int measuredChunkWaitTicks;

    private AotakeNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) {
            MinecraftForge.EVENT_BUS.addListener(AotakeNetworkSmokeServerRunner::onServerTick);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
            if (server == null || !server.isRunning()) {
                return;
            }
            if (sparkProfile != null && sparkProfile.writeWhenComplete()) {
                AotakeNetworkSmokeStatus.append("PASS spark-report-written");
            }
            if (finished) {
                if (sparkProfile != null && !sparkProfile.written()) {
                    return;
                }
                shutdownWhenSaved(server);
                return;
            }
            if (!ready) {
                ready = true;
                AotakeNetworkSmokeStatus.append("PASS server-ready");
            }
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayer player = players.get(0);
            if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
                runWritePhase(player);
            } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
                runVerifyPhase(player);
            } else {
                throw new IllegalStateException("Unknown network smoke phase: " + AotakeNetworkSmokeStatus.phase());
            }
        } catch (Throwable error) {
            finished = true;
            AotakeNetworkSmokeStatus.append("FAIL server " + error);
            throw error;
        }
    }

    /**
     * 留出玩家退出和数据落盘时间，再走 Minecraft 自身的正常关闭流程。
     */
    private static void shutdownWhenSaved(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() > 0) {
            shutdownTicks = 0;
            return;
        }
        if (++shutdownTicks >= 40) {
            AotakeNetworkSmokeStatus.append("PASS server-shutdown");
            server.halt(false);
        }
    }

    private static void runWritePhase(ServerPlayer player) {
        if (!AotakeNetworkSmokeNotifications.sendWhenReady(player)) return;
        if (!conciseCommandVerified) {
            if (!verifyConciseCommandRefresh(player)) {
                return;
            }
            conciseCommandVerified = true;
        }
        if (!runGameplay(player)) {
            return;
        }
        if (!commandVerified) {
            int commandResult = player.getServer().getCommands().performCommand(
                    player.createCommandSourceStack().withPermission(4),
                    "aotake config common base.batch.sweepBatchLimit " + SENTINEL_CONFIG_VALUE);
            if (commandResult <= 0 || CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
                throw new IllegalStateException("Config command did not update sweepBatchLimit");
            }
            AotakeNetworkSmokeStatus.append("PASS config-command-roundtrip");
            commandVerified = true;
        }

        PlayerSweepData playerData = PlayerSweepData.getData(player);
        if (playerData.isShowSweepResult() || playerData.isEnableWarningVoice()) {
            return;
        }
        WorldTrashData.getTrashContainer(player, 1);
        WorldTrashData trashData = WorldTrashData.get(player);
        SimpleContainer firstPage = trashData.getInventoryList().get(0);
        ItemStack displaced = firstPage.getItem(0);
        if (!displaced.isEmpty()) {
            trashData.getDropList().add(new xin.vanilla.banira.common.data.KeyValue<>(
                    new xin.vanilla.banira.common.data.WorldCoordinate(player), displaced.copy()));
        }
        firstPage.setItem(0, SENTINEL_STACK.copy());
        trashData.setDirty();
        writeCheckpoint(player);
        AotakeNetworkSmokeStatus.append("PASS server-config-roundtrip");
        AotakeNetworkSmokeStatus.append("PASS phase-one-world-write");
        xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeConfigs.verify(false);
        AotakeNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    private static boolean runGameplay(ServerPlayer player) {
        if (++gameplayTicks > 2400) {
            throw new IllegalStateException("Gameplay smoke timed out in " + gameplayStep);
        }
        ServerLevel level = player.getLevel();
        switch (gameplayStep) {
            case PREPARE:
                configureGameplayFixture();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.setNoGravity(true);
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                countdownItem = new ItemEntity(level, fixtureX(player), fixtureY(player), fixtureZ(player), new ItemStack(Items.DIAMOND));
                stabilizeFixture(countdownItem, "countdown");
                level.addFreshEntity(countdownItem);
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START countdown-cleanup");
                gameplayStep = GameplayStep.WAIT_COUNTDOWN;
                return false;
            case WAIT_COUNTDOWN:
                if (countdownItem.isAlive()) return false;
                requireRecovered(Items.DIAMOND, 1);
                AotakeNetworkSmokeStatus.append("PASS countdown-cleanup");
                List<ItemEntity> items = new ArrayList<>();
                for (int index = 0; index < 4; index++) {
                    ItemEntity item = new ItemEntity(level, fixtureX(player) + index * 0.1D,
                            fixtureY(player), fixtureZ(player), new ItemStack(Items.GOLD_INGOT));
                    stabilizeFixture(item, "chunk-" + index);
                    level.addFreshEntity(item);
                    items.add(item);
                }
                chunkItems = items;
                AotakeNetworkSmokeStatus.append("START chunk-cleanup");
                gameplayStep = GameplayStep.WAIT_CHUNK;
                return false;
            case WAIT_CHUNK:
                long visibleCandidates = AotakeUtils.getAllEntitiesByFilter(null, true).stream()
                        .filter(chunkItems::contains)
                        .count();
                if (!chunkCandidatesVisible) {
                    if (visibleCandidates < chunkItems.size()) {
                        if (++chunkWaitTicks > 100) {
                            throw new IllegalStateException("Chunk cleanup candidates were not visible: "
                                    + visibleCandidates + "/" + chunkItems.size() + " alive="
                                    + chunkItems.stream().filter(Entity::isAlive).count()
                                    + " rules=" + CommonConfig.get().base().chunk().chunkCheckEntityList()
                                    + " mode=" + CommonConfig.get().base().chunk().chunkCheckEntityListMode());
                        }
                        return false;
                    }
                    chunkCandidatesVisible = true;
                    chunkWaitTicks = 0;
                    CommonConfig.get().base().chunk().chunkCheckInterval(1L);
                    AotakeNetworkSmokeStatus.append("PASS chunk-candidates-visible");
                }
                if (chunkItems.stream().anyMatch(Entity::isAlive)) {
                    if (++chunkWaitTicks > 200) {
                        throw new IllegalStateException("Chunk cleanup did not remove visible candidates");
                    }
                    return false;
                }
                AotakeNetworkSmokeStatus.append("PASS chunk-cleanup");
                requireRecovered(Items.GOLD_INGOT, 4);
                CommonConfig.get().base().chunk().chunkCheckInterval(0L);
                burstDropItems = spawnItems(level, fixtureX(player), fixtureY(player), fixtureZ(player), 128, 0.9D);
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START burst-drop-cleanup");
                gameplayStep = GameplayStep.WAIT_BURST_DROP;
                return false;
            case WAIT_BURST_DROP:
                if (burstDropItems.stream().anyMatch(Entity::isAlive)) return false;
                requireRecovered(Items.PAPER, 128);
                AotakeNetworkSmokeStatus.append("PASS burst-drop-cleanup");
                CommonConfig.get().base().chunk().chunkCheckInterval(1L).chunkCheckLimit(512);
                globalSweepItems = new ArrayList<>();
                for (int chunk = 0; chunk < 4; chunk++) {
                    globalSweepItems.addAll(spawnItems(level, fixtureX(player) + chunk * 17.0D,
                            fixtureY(player), fixtureZ(player), 48, 0.9D));
                }
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START global-batch-cleanup");
                gameplayStep = GameplayStep.WAIT_GLOBAL_BATCH;
                return false;
            case WAIT_GLOBAL_BATCH:
                if (globalSweepItems.stream().anyMatch(Entity::isAlive)) return false;
                requireRecovered(Items.PAPER, 320);
                AotakeNetworkSmokeStatus.append("PASS global-batch-cleanup");
                captureTarget = EntityType.COW.create(level);
                if (captureTarget == null) throw new IllegalStateException("Could not create capture target");
                captureTarget.moveTo(24.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                level.addFreshEntity(captureTarget);
                player.teleportTo(level, 23.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                player.getInventory().add(new ItemStack(Items.STICK));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                player.setShiftKeyDown(true);
                player.setPose(Pose.CROUCHING);
                PlayerInteractEvent.EntityInteractSpecific event = new PlayerInteractEvent.EntityInteractSpecific(
                        player, InteractionHand.MAIN_HAND, captureTarget, Vec3.ZERO);
                EventHandlerProxy.onRightEntity(event);
                player.setShiftKeyDown(false);
                player.setPose(Pose.STANDING);
                if (!event.isCanceled() || event.getCancellationResult() != InteractionResult.SUCCESS) {
                    throw new IllegalStateException("Entity capture interaction was not consumed");
                }
                AotakeNetworkSmokeStatus.append("START entity-capture");
                gameplayStep = GameplayStep.WAIT_CAPTURE;
                return false;
            case WAIT_CAPTURE:
                if (captureTarget.isAlive()) return false;
                if (player.getInventory().items.stream().noneMatch(AotakeUtils::hasAotakeTag)) {
                    throw new IllegalStateException("Captured entity payload was not added to inventory");
                }
                AotakeNetworkSmokeStatus.append("PASS entity-capture");
                long preparedAt = System.nanoTime();
                measuredWorkload = new AotakeNetworkSmokeWorkload(player);
                AotakeNetworkSmokeStatus.append("PASS measured-fixture preparation-ns=" + (System.nanoTime() - preparedAt));
                gameplayStep = GameplayStep.WAIT_MEASURED_CHUNKS;
                return false;
            case WAIT_MEASURED_CHUNKS:
                // Loading FULL chunks does not make their entity sections queryable yet.
                if (!measuredWorkload.chunksReady()) {
                    if (++measuredChunkWaitTicks > 200) throw new IllegalStateException("Measured chunks did not become ready");
                    return false;
                }
                AotakeNetworkSmokeStatus.append("PASS measured-chunks-ready chunks=4 wait-ticks=" + measuredChunkWaitTicks);
                sparkProfile = ReflectiveSparkProfile.start(player.getServer());
                AotakeNetworkSmokeStatus.append("PASS spark-profiler-active");
                AotakeNetworkSmokeStatus.append("PASS sustained-ready");
                AotakeNetworkSmokeStatus.append("START sustained-cleanup-workload");
                gameplayStep = GameplayStep.SUSTAIN_CLEANUP;
                return false;
            case SUSTAIN_CLEANUP:
                AotakeNetworkSmokeProfilePlan.shouldContinue(sparkProfile.samplingEnded(), measuredWorkload.cycles());
                boolean pending = !measuredWorkload.complete();
                boolean complete = measuredWorkload.tick();
                if (pending && sparkProfile.samplingEnded()) {
                    throw new IllegalStateException("Sampler expired during measured cleanup operations");
                }
                if (!complete || !sparkProfile.written()) return false;
                measuredWorkload.report();
                AotakeNetworkSmokeStatus.append("PASS sustained-cleanup-workload");
                gameplayStep = GameplayStep.COMPLETE;
                return true;
            case COMPLETE:
                return true;
            default:
                throw new IllegalStateException("Unknown gameplay step " + gameplayStep);
        }
    }

    private static void configureGameplayFixture() {
        CommonConfigView.BaseView base = CommonConfig.get().base();
        base.sweep().sweepWhenNoPlayer(true).sweepInterval(TimeUnit.HOURS.toMillis(1L))
                .entityList(Collections.singletonList("minecraft:item"))
                .entityListMode(EnumListType.BLACK).entityListLimit(Integer.MAX_VALUE);
        base.chunk().chunkCheckInterval(0L).chunkCheckLimit(2).chunkCheckRetain(0.0D)
                .chunkCheckNotice(false).chunkCheckMode(EnumChunkCheckMode.DEFAULT)
                .chunkCheckEntityList(Collections.singletonList("minecraft:item"))
                .chunkCheckEntityListMode(EnumListType.BLACK).chunkCheckOnlyNotice(false)
                .chunkVaultEnabled(false);
        base.entityCatch().allowCatchEntity(true).catchItem(Collections.singletonList("minecraft:stick"));
        base.batch().sweepEntityLimit(128).sweepEntityInterval(1).sweepBatchLimit(8);
    }

    private static double fixtureX(ServerPlayer player) {
        return (player.blockPosition().getX() >> 4) * 16 + 8.5D;
    }

    private static double fixtureY(ServerPlayer player) {
        return player.getY() + 2.0D;
    }

    private static double fixtureZ(ServerPlayer player) {
        return (player.blockPosition().getZ() >> 4) * 16 + 8.5D;
    }

    private static List<ItemEntity> spawnItems(ServerLevel level, double x, double y, double z,
                                               int count, double spacing) {
        List<ItemEntity> items = new ArrayList<>(count);
        int columns = (int) Math.ceil(Math.sqrt(count));
        for (int index = 0; index < count; index++) {
            ItemEntity item = new ItemEntity(level, x + (index % columns) * spacing,
                    y, z + (index / columns) * spacing, new ItemStack(Items.PAPER));
            stabilizeFixture(item, x + ":" + index);
            level.addFreshEntity(item);
            items.add(item);
        }
        return items;
    }

    private static void stabilizeFixture(ItemEntity item, String identity) {
        item.getItem().getOrCreateTag().putString("aotakeSmokeWarmup", identity);
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        item.setPickUpDelay(32767);
    }

    private static void requireRecovered(net.minecraft.world.item.Item item, int expected) {
        int actual = AotakeNetworkSmokeWorkload.recoveredItem(item);
        if (actual != expected) throw new IllegalStateException("Warmup recovery mismatch: " + item
                + " expected=" + expected + " actual=" + actual);
    }

    /**
     * 通过真实配置指令触发与配置界面同步相同的 save listener，并检查命令树增删。
     */
    private static boolean verifyConciseCommandRefresh(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        String rootName = CommonConfig.get().command().commandClearDrop();
        boolean registered = server.getCommands().getDispatcher().getRoot().getChild(rootName) != null;
        boolean enabled = CommonConfig.get().concise().conciseClearDrop();

        if (++commandRefreshWaitTicks > 100) {
            throw new IllegalStateException("Timed out waiting for concise command tree refresh");
        }
        switch (commandRefreshStage) {
            case 0:
                runConfigCommand(player, "concise.conciseClearDrop", true);
                commandRefreshStage = 1;
                return false;
            case 1:
                if (!enabled || !registered) {
                    return false;
                }
                if (++commandRefreshSettleTicks < 10) {
                    return false;
                }
                commandRefreshSettleTicks = 0;
                runConfigCommand(player, "concise.conciseClearDrop", false);
                commandRefreshStage = 2;
                return false;
            case 2:
                if (enabled || registered) {
                    return false;
                }
                if (++commandRefreshSettleTicks < 10) {
                    return false;
                }
                commandRefreshSettleTicks = 0;
                runConfigCommand(player, "concise.conciseClearDrop", true);
                commandRefreshStage = 3;
                return false;
            case 3:
                if (!enabled || !registered) {
                    return false;
                }
                if (++commandRefreshSettleTicks < 10) {
                    return false;
                }
                AotakeNetworkSmokeStatus.append("PASS concise-command-live-refresh");
                return true;
            default:
                throw new IllegalStateException("Unknown command refresh stage: " + commandRefreshStage);
        }
    }

    private static void runConfigCommand(ServerPlayer player, String path, boolean value) {
        int result = player.getServer().getCommands().performCommand(
                player.createCommandSourceStack().withPermission(4),
                "aotake config common " + path + " " + value);
        if (result <= 0) {
            throw new IllegalStateException("Config command failed for " + path + "=" + value);
        }
        commandRefreshWaitTicks = 0;
    }

    private static void runVerifyPhase(ServerPlayer player) {
        if (!AotakeNetworkSmokeNotifications.sendWhenReady(player)) return;
        java.util.Properties checkpoint = new java.util.Properties();
        try (java.io.Reader reader = Files.newBufferedReader(checkpointPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            checkpoint.load(reader);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Missing restart checkpoint", error);
        }
        if (!player.getUUID().toString().equals(checkpoint.getProperty("player"))
                || Integer.parseInt(checkpoint.getProperty("cycles")) != AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES
                || Integer.parseInt(checkpoint.getProperty("paper")) != expectedPaper()
                || AotakeNetworkSmokeWorkload.recoveredPaper() != expectedPaper()
                || Integer.parseInt(checkpoint.getProperty("captured")) != 20
                || AotakeNetworkSmokeWorkload.capturedCount(player) != 20) {
            throw new IllegalStateException("Final cleanup payload was not restored: paper="
                    + AotakeNetworkSmokeWorkload.recoveredPaper() + " captured=" + AotakeNetworkSmokeWorkload.capturedCount(player));
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-final-cycle cycles=20 paper=" + expectedPaper() + " captured=20");
        if (CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
            throw new IllegalStateException("Command config value was not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-command-config");

        PlayerSweepData playerData = PlayerSweepData.getData(player);
        if (playerData.isShowSweepResult() || playerData.isEnableWarningVoice()) {
            throw new IllegalStateException("Player preferences were not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-player-data");

        WorldTrashData trashData = WorldTrashData.get(player);
        if (trashData.getInventoryList().isEmpty()) {
            throw new IllegalStateException("Persisted dustbin page is missing");
        }
        ItemStack stack = trashData.getInventoryList().get(0).getItem(0);
        if (stack.getItem() != Items.EMERALD || stack.getCount() != SENTINEL_COUNT) {
            throw new IllegalStateException("Persisted dustbin sentinel is missing: " + stack);
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data");
        xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeConfigs.verify(false);
        AotakeNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }

    private static Path checkpointPath() {
        String value = System.getProperty("aotake.networkSmoke.checkpoint", "").trim();
        if (value.isEmpty()) throw new IllegalStateException("Missing cleanup checkpoint path");
        return Paths.get(value);
    }

    private static void writeCheckpoint(ServerPlayer player) {
        int paper = AotakeNetworkSmokeWorkload.recoveredPaper();
        int captured = AotakeNetworkSmokeWorkload.capturedCount(player);
        if (paper != expectedPaper() || captured != 20 || measuredWorkload.cycles() != 20) {
            throw new IllegalStateException("Incomplete final cleanup checkpoint");
        }
        java.util.Properties checkpoint = new java.util.Properties();
        checkpoint.setProperty("player", player.getUUID().toString());
        checkpoint.setProperty("cycles", Integer.toString(measuredWorkload.cycles()));
        checkpoint.setProperty("paper", Integer.toString(paper));
        checkpoint.setProperty("captured", Integer.toString(captured));
        try (java.io.Writer writer = Files.newBufferedWriter(checkpointPath(), java.nio.charset.StandardCharsets.UTF_8)) {
            checkpoint.store(writer, "Aotake final cleanup checkpoint");
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Unable to write cleanup checkpoint", error);
        }
        AotakeNetworkSmokeStatus.append("PASS final-checkpoint cycles=20 paper=" + paper + " captured=20");
    }

    private static int expectedPaper() {
        return AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES * AotakeNetworkSmokeWorkload.PAPER_PER_CYCLE;
    }

    private enum GameplayStep {
        PREPARE,
        WAIT_COUNTDOWN,
        WAIT_CHUNK,
        WAIT_BURST_DROP,
        WAIT_GLOBAL_BATCH,
        WAIT_CAPTURE,
        WAIT_MEASURED_CHUNKS,
        SUSTAIN_CLEANUP,
        COMPLETE
    }

    /** Spark 1.10 sampler/export API, isolated from production dependencies. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ReflectiveSparkProfile {
        private final Object sampler, platform, plugin;
        private final Future<?> future;
        private final MinecraftServer server;
        private final Path report;
        private final long startedAt = System.nanoTime();
        private boolean written;
        private boolean stopRequested;

        private ReflectiveSparkProfile(Object sampler, Future<?> future, Object platform, Object plugin,
                                       MinecraftServer server, Path report) {
            this.sampler = sampler;
            this.future = future;
            this.platform = platform;
            this.plugin = plugin;
            this.server = server;
            this.report = report;
        }

        private static ReflectiveSparkProfile start(MinecraftServer server) {
            try {
                String configured = AotakeNetworkSmokeStatus.sparkReport();
                if (configured.isEmpty()) throw new IllegalStateException("Missing Spark report path");
                Object plugin = plugin();
                Class<?> base = base(plugin);
                Field platformField = base.getDeclaredField("platform");
                platformField.setAccessible(true);
                Object platform = platformField.get(plugin);
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 10.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class)
                        .invoke(builder, SPARK_SAMPLE_SECONDS, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                Class<?> gameThread = Class.forName("me.lucko.spark.common.sampler.ThreadDumper$GameThread", true, loader);
                Object gameThreadDumper = gameThread.getConstructor().newInstance();
                gameThread.getMethod("setThread", Thread.class).invoke(gameThreadDumper, Thread.currentThread());
                builderType.getMethod("threadDumper", dumperType)
                        .invoke(builder, gameThread.getMethod("get").invoke(gameThreadDumper));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", grouperType).invoke(builder, grouperType.getField("BY_POOL").get(null));
                Object container = platform.getClass().getMethod("getSamplerContainer").invoke(platform);
                method(container.getClass(), "stopActiveSampler", 1).invoke(container, false);
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                method(container.getClass(), "setActiveSampler", 1).invoke(container, sampler);
                return new ReflectiveSparkProfile(sampler, future, platform, plugin, server, Paths.get(configured).toAbsolutePath());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start Spark sampler", error);
            }
        }

        private boolean written() {
            return written;
        }

        private boolean samplingEnded() {
            return stopRequested || future.isDone()
                    || System.nanoTime() - startedAt >= TimeUnit.SECONDS.toNanos(SPARK_SAMPLE_SECONDS);
        }

        private boolean writeWhenComplete() {
            if (!stopRequested && System.nanoTime() - startedAt >= TimeUnit.SECONDS.toNanos(SPARK_SAMPLE_SECONDS)) {
                try {
                    method(sampler.getClass(), "stop", 1).invoke(sampler, false);
                    stopRequested = true;
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Unable to stop Spark sampler", error);
                }
            }
            if (written || (!stopRequested && !future.isDone())) return false;
            try {
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
                Object props = propsType.getConstructor().newInstance();
                Class<?> senderData = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data", true, loader);
                propsType.getMethod("creator", senderData).invoke(props,
                        senderData.getConstructor(String.class, java.util.UUID.class)
                                .newInstance("Aotake network smoke", null));
                Class<?> disambiguatorType = Class.forName("me.lucko.spark.common.util.MethodDisambiguator", true, loader);
                Class<?> mergeType = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                Supplier<Object> merge = () -> createMergeMode(mergeType, disambiguatorType);
                propsType.getMethod("mergeMode", Supplier.class).invoke(props, merge);
                propsType.getMethod("classSourceLookup", Supplier.class)
                        .invoke(props, (Supplier<Object>) () -> createClassSourceLookup(plugin));
                Object proto = method(sampler.getClass(), "toProto", 2).invoke(sampler, platform, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark report was empty");
                Files.createDirectories(report.getParent());
                Files.write(report, bytes);
                written = true;
                return true;
            } catch (ReflectiveOperationException | java.io.IOException error) {
                throw new IllegalStateException("Unable to write Spark report", error);
            }
        }

        private static Object plugin() throws ReflectiveOperationException {
            Field listeners = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("listeners");
            listeners.setAccessible(true);
            for (Object candidate : ((Map<?, ?>) listeners.get(MinecraftForge.EVENT_BUS)).keySet()) {
                if (candidate != null && candidate.getClass().getName()
                        .equals("me.lucko.spark.forge.plugin.ForgeServerSparkPlugin")) return candidate;
            }
            throw new IllegalStateException("Spark Forge server plugin was not registered");
        }

        private static Class<?> base(Object plugin) {
            Class<?> type = plugin.getClass();
            while (type != null && !type.getName().equals("me.lucko.spark.forge.plugin.ForgeSparkPlugin")) {
                type = type.getSuperclass();
            }
            if (type == null) throw new IllegalStateException("Spark base plugin was not found");
            return type;
        }

        private static Object createMergeMode(Class<?> mergeType, Class<?> disambiguatorType) {
            try {
                return mergeType.getMethod("sameMethod", disambiguatorType)
                        .invoke(null, disambiguatorType.getConstructor().newInstance());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark merge mode", error);
            }
        }

        private static Object createClassSourceLookup(Object plugin) {
            try {
                return plugin.getClass().getMethod("createClassSourceLookup").invoke(plugin);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark class source lookup", error);
            }
        }

        private static Method method(Class<?> type, String name, int parameters) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) return candidate;
            }
            throw new IllegalStateException("Missing Spark method " + name);
        }
    }
}
