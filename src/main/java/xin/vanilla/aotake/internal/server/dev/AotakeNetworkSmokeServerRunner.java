package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
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

/**
 * 在独立服务端内写入并复核网络 smoke 的持久化哨兵。
 */
public final class AotakeNetworkSmokeServerRunner {
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
    private static CowEntity captureTarget;
    private static ReflectiveSparkProfile sparkProfile;
    private static AotakeNetworkSmokeWorkload measuredWorkload;

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
            List<ServerPlayerEntity> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayerEntity player = players.get(0);
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

    private static void runWritePhase(ServerPlayerEntity player) {
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
        Inventory firstPage = trashData.getInventoryList().get(0);
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
        AotakeNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    private static boolean runGameplay(ServerPlayerEntity player) {
        if (++gameplayTicks > 2400) {
            throw new IllegalStateException("Gameplay smoke timed out in " + gameplayStep);
        }
        ServerWorld level = player.getLevel();
        switch (gameplayStep) {
            case PREPARE:
                configureGameplayFixture();
                player.setGameMode(net.minecraft.world.GameType.CREATIVE);
                player.setNoGravity(true);
                player.abilities.flying = true;
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
                player.inventory.add(new ItemStack(Items.STICK));
                player.setItemInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
                player.setShiftKeyDown(true);
                player.setPose(Pose.CROUCHING);
                PlayerInteractEvent.EntityInteractSpecific event = new PlayerInteractEvent.EntityInteractSpecific(
                        player, Hand.MAIN_HAND, captureTarget, Vector3d.ZERO);
                EventHandlerProxy.onRightEntity(event);
                player.setShiftKeyDown(false);
                player.setPose(Pose.STANDING);
                if (!event.isCanceled() || event.getCancellationResult() != ActionResultType.SUCCESS) {
                    throw new IllegalStateException("Entity capture interaction was not consumed");
                }
                AotakeNetworkSmokeStatus.append("START entity-capture");
                gameplayStep = GameplayStep.WAIT_CAPTURE;
                return false;
            case WAIT_CAPTURE:
                if (captureTarget.isAlive()) return false;
                if (player.inventory.items.stream().noneMatch(AotakeUtils::hasAotakeTag)) {
                    throw new IllegalStateException("Captured entity payload was not added to inventory");
                }
                AotakeNetworkSmokeStatus.append("PASS entity-capture");
                long preparedAt = System.nanoTime();
                measuredWorkload = new AotakeNetworkSmokeWorkload(player);
                AotakeNetworkSmokeStatus.append("PASS measured-fixture preparation-ns=" + (System.nanoTime() - preparedAt));
                sparkProfile = ReflectiveSparkProfile.start();
                AotakeNetworkSmokeStatus.append("PASS spark-profiler-active");
                AotakeNetworkSmokeStatus.append("PASS sustained-ready");
                AotakeNetworkSmokeStatus.append("START sustained-cleanup-workload");
                gameplayStep = GameplayStep.SUSTAIN_CLEANUP;
                return false;
            case SUSTAIN_CLEANUP:
                AotakeNetworkSmokeProfilePlan.shouldContinue(sparkProfile.future.isDone(), measuredWorkload.cycles());
                boolean pending = !measuredWorkload.complete();
                boolean complete = measuredWorkload.tick();
                if (pending && sparkProfile.future.isDone()) {
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
        CommonConfig.BaseView base = CommonConfig.get().base();
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

    private static double fixtureX(ServerPlayerEntity player) {
        return (player.blockPosition().getX() >> 4) * 16 + 8.5D;
    }

    private static double fixtureY(ServerPlayerEntity player) {
        return player.getY() + 2.0D;
    }

    private static double fixtureZ(ServerPlayerEntity player) {
        return (player.blockPosition().getZ() >> 4) * 16 + 8.5D;
    }

    private static List<ItemEntity> spawnItems(ServerWorld level, double x, double y, double z,
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
        item.setDeltaMovement(Vector3d.ZERO);
        item.setPickUpDelay(32767);
    }

    private static void requireRecovered(net.minecraft.item.Item item, int expected) {
        int actual = AotakeNetworkSmokeWorkload.recoveredItem(item);
        if (actual != expected) throw new IllegalStateException("Warmup recovery mismatch: " + item
                + " expected=" + expected + " actual=" + actual);
    }

    /**
     * 通过真实配置指令触发与配置界面同步相同的 save listener，并检查命令树增删。
     */
    private static boolean verifyConciseCommandRefresh(ServerPlayerEntity player) {
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

    private static void runConfigCommand(ServerPlayerEntity player, String path, boolean value) {
        int result = player.getServer().getCommands().performCommand(
                player.createCommandSourceStack().withPermission(4),
                "aotake config common " + path + " " + value);
        if (result <= 0) {
            throw new IllegalStateException("Config command failed for " + path + "=" + value);
        }
        commandRefreshWaitTicks = 0;
    }

    private static void runVerifyPhase(ServerPlayerEntity player) {
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
        AotakeNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }

    private static Path checkpointPath() {
        String value = System.getProperty("aotake.networkSmoke.checkpoint", "").trim();
        if (value.isEmpty()) throw new IllegalStateException("Missing cleanup checkpoint path");
        return Paths.get(value);
    }

    private static void writeCheckpoint(ServerPlayerEntity player) {
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
        SUSTAIN_CLEANUP,
        COMPLETE
    }

    /** Spark 1.9 does not expose a stable cross-loader report API. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ReflectiveSparkProfile {
        private final Object sampler;
        private final Future<?> future;
        private final Object platform;
        private final Object plugin;
        private final MinecraftServer server;
        private final Path report;
        private boolean written;

        private ReflectiveSparkProfile(Object sampler, Future<?> future, Object platform, Object plugin,
                                       MinecraftServer server, Path report) {
            this.sampler = sampler;
            this.future = future;
            this.platform = platform;
            this.plugin = plugin;
            this.server = server;
            this.report = report;
        }

        private static ReflectiveSparkProfile start() {
            try {
                String configured = AotakeNetworkSmokeStatus.sparkReport();
                if (configured.isEmpty()) throw new IllegalStateException("Missing Spark report path");
                MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
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
                        .invoke(builder, 60L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                Field threadDumper = base.getDeclaredField("threadDumper");
                threadDumper.setAccessible(true);
                Object setup = threadDumper.get(plugin);
                setup.getClass().getMethod("ensureSetup").invoke(setup);
                builderType.getMethod("threadDumper", dumperType)
                        .invoke(builder, base.getMethod("getDefaultThreadDumper").invoke(plugin));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", grouperType).invoke(builder, grouperType.getField("BY_POOL").get(null));
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveSparkProfile(sampler, future, platform, plugin, server, Paths.get(configured).toAbsolutePath());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start Spark sampler", error);
            }
        }

        private boolean written() {
            return written;
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                future.get();
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> sourceType = Class.forName("net.minecraft.command.ICommandSource", true, loader);
                Class<?> base = Class.forName("me.lucko.spark.forge.plugin.ForgeSparkPlugin", true, loader);
                Class<?> senderType = Class.forName("me.lucko.spark.forge.ForgeCommandSender", true, loader);
                Object sender = senderType.getConstructor(sourceType, base).newInstance(server, plugin);
                Class<?> orderType = Class.forName("me.lucko.spark.common.sampler.ThreadNodeOrder", true, loader);
                Class<?> disambiguatorType = Class.forName("me.lucko.spark.common.util.MethodDisambiguator", true, loader);
                Class<?> mergeType = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                Object merge = mergeType.getMethod("sameMethod", disambiguatorType)
                        .invoke(null, disambiguatorType.getConstructor().newInstance());
                Object lookup = base.getMethod("createClassSourceLookup").invoke(plugin);
                Object proto = method(sampler.getClass(), "toProto", 6).invoke(sampler, platform, sender,
                        orderType.getField("BY_TIME").get(null), "Aotake network smoke", merge, lookup);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark report was empty");
                Files.createDirectories(report.getParent());
                Files.write(report, bytes);
                written = true;
                return true;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted writing Spark report", error);
            } catch (ReflectiveOperationException | java.io.IOException | java.util.concurrent.ExecutionException error) {
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

        private static Method method(Class<?> type, String name, int parameters) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) return candidate;
            }
            throw new IllegalStateException("Missing Spark method " + name);
        }
    }
}
