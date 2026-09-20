package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
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
import xin.vanilla.banira.api.event.BaniraEvents;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 真实专服内验证 Aotake 的玩法链、持久化与 Spark 原生报告。 */
public final class AotakeNetworkSmokeServerRunner {
    private static final int SENTINEL_COUNT = 7;
    private static final int SENTINEL_CONFIG_VALUE = 11;

    private static boolean ready;
    private static boolean conciseCommandVerified;
    private static boolean commandVerified;
    private static boolean finished;
    private static boolean failed;
    private static int shutdownTicks;
    private static int gameplayTicks;
    private static int chunkWaitTicks;
    private static int commandRefreshStage;
    private static int commandRefreshWaitTicks;
    private static int commandRefreshSettleTicks;
    private static boolean chunkCandidatesVisible;
    private static GameplayStep gameplayStep = GameplayStep.PREPARE;
    private static ItemEntity countdownItem;
    private static List<ItemEntity> chunkItems = Collections.emptyList();
    private static List<ItemEntity> burstDropItems = Collections.emptyList();
    private static List<ItemEntity> globalSweepItems = Collections.emptyList();
    private static Cow captureTarget;
    private static ReflectiveSparkProfile sparkProfile;
    private static AotakeNetworkSmokeWorkload measuredWorkload;

    private AotakeNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) {
            BaniraEvents.Server.onTick(event -> onTick());
        }
    }

    private static void onTick() {
        try {
            MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
            if (server == null || !server.isRunning()) return;
            if (sparkProfile != null && sparkProfile.writeWhenComplete()) {
                AotakeNetworkSmokeStatus.append("PASS spark-report-written");
            }
            if (failed) {
                shutdownWhenSaved(server);
                return;
            }
            if (finished) {
                if (!AotakeNetworkSmokeServerPlan.mayShutdown(AotakeNetworkSmokeStatus.phase(), true,
                        sparkProfile == null || sparkProfile.written())) {
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
            if (players.isEmpty()) return;
            ServerPlayer player = players.get(0);
            if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
                runWritePhase(player);
            } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
                runVerifyPhase(player);
            } else {
                throw new IllegalStateException("Unknown network smoke phase: " + AotakeNetworkSmokeStatus.phase());
            }
        } catch (Throwable error) {
            if (failed) return;
            failed = true;
            finished = true;
            AotakeNetworkSmokeStatus.append("FAIL server " + error);
            throw new IllegalStateException("Aotake network smoke server failed", error);
        }
    }

    private static void runWritePhase(ServerPlayer player) {
        if (!conciseCommandVerified && !verifyConciseCommandRefresh(player)) return;
        conciseCommandVerified = true;
        if (!runGameplay(player)) return;
        if (!commandVerified) {
            int result = player.getServer().getCommands().performCommand(
                    player.createCommandSourceStack().withPermission(4),
                    "aotake config common base.batch.sweepBatchLimit " + SENTINEL_CONFIG_VALUE);
            if (result <= 0 || CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
                throw new IllegalStateException("Config command did not update sweepBatchLimit");
            }
            AotakeNetworkSmokeStatus.append("PASS config-command-roundtrip");
            commandVerified = true;
        }
        PlayerSweepData data = PlayerSweepData.getData(player);
        if (data.isShowSweepResult() || data.isEnableWarningVoice()) return;
        WorldTrashData.getTrashContainer(player, 1);
        SimpleContainer inventory = WorldTrashData.get(player).getInventoryList().get(0);
        ItemStack displaced = inventory.getItem(0);
        if (!displaced.isEmpty()) {
            WorldTrashData.get(player).getDropList().add(new xin.vanilla.banira.common.data.KeyValue<>(
                    new xin.vanilla.banira.common.data.WorldCoordinate(player), displaced.copy()));
        }
        inventory.setItem(0, new ItemStack(Items.EMERALD, SENTINEL_COUNT));
        WorldTrashData.get(player).setDirty();
        AotakeNetworkSmokeStatus.append("PASS server-config-roundtrip");
        AotakeNetworkSmokeStatus.append("PASS phase-one-world-write");
        CommonConfig.save();
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
                ItemStack ordinary = new ItemStack(Items.APPLE);
                player.setItemInHand(InteractionHand.MAIN_HAND, ordinary);
                net.minecraft.world.InteractionResultHolder<ItemStack> vanilla =
                        EventHandlerProxy.onPlayerUseItem(player, level, InteractionHand.MAIN_HAND);
                if (vanilla.getResult() != InteractionResult.PASS || vanilla.getObject() != ordinary) {
                    throw new IllegalStateException("Ordinary item use was intercepted");
                }
                AotakeNetworkSmokeStatus.append("PASS vanilla-item-use");
                countdownItem = new ItemEntity(level, fixtureX(player), fixtureY(player), fixtureZ(player),
                        new ItemStack(Items.DIAMOND));
                level.addFreshEntity(countdownItem);
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START countdown-cleanup");
                gameplayStep = GameplayStep.WAIT_COUNTDOWN;
                return false;
            case WAIT_COUNTDOWN:
                if (countdownItem.isAlive()) return false;
                AotakeNetworkSmokeStatus.append("PASS countdown-cleanup");
                List<ItemEntity> items = new ArrayList<>();
                for (int index = 0; index < 4; index++) {
                    ItemEntity item = new ItemEntity(level, fixtureX(player) + index * 0.1D,
                            fixtureY(player), fixtureZ(player), new ItemStack(Items.GOLD_INGOT));
                    level.addFreshEntity(item);
                    items.add(item);
                }
                chunkItems = items;
                CommonConfig.get().base().chunk().chunkCheckInterval(1L);
                AotakeNetworkSmokeStatus.append("START chunk-cleanup");
                gameplayStep = GameplayStep.WAIT_CHUNK;
                return false;
            case WAIT_CHUNK:
                long visible = AotakeUtils.getAllEntitiesByFilter(null, true).stream()
                        .filter(chunkItems::contains)
                        .count();
                if (!chunkCandidatesVisible) {
                    if (visible < chunkItems.size()) {
                        if (++chunkWaitTicks > 100) {
                            throw new IllegalStateException("Chunk cleanup candidates were not visible: "
                                    + visible + "/" + chunkItems.size());
                        }
                        return false;
                    }
                    chunkCandidatesVisible = true;
                    chunkWaitTicks = 0;
                    AotakeNetworkSmokeStatus.append("PASS chunk-candidates-visible");
                }
                if (chunkItems.stream().anyMatch(Entity::isAlive)) {
                    if (++chunkWaitTicks > 240) {
                        throw new IllegalStateException("Chunk cleanup did not remove visible candidates");
                    }
                    return false;
                }
                AotakeNetworkSmokeStatus.append("PASS chunk-cleanup");
                CommonConfig.get().base().chunk().chunkCheckInterval(0L);
                burstDropItems = spawnItems(level, fixtureX(player), fixtureY(player), fixtureZ(player), 128, 0.9D);
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START burst-drop-cleanup");
                gameplayStep = GameplayStep.WAIT_BURST_DROP;
                return false;
            case WAIT_BURST_DROP:
                if (burstDropItems.stream().anyMatch(Entity::isAlive)) return false;
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
                AotakeNetworkSmokeStatus.append("PASS global-batch-cleanup");
                captureTarget = EntityType.COW.create(level);
                if (captureTarget == null) throw new IllegalStateException("Could not create capture target");
                captureTarget.moveTo(24.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                level.addFreshEntity(captureTarget);
                player.teleportTo(level, 23.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                player.inventory.add(new ItemStack(Items.STICK));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
                player.setShiftKeyDown(true);
                player.setPose(Pose.CROUCHING);
                InteractionResult interaction = EventHandlerProxy.onRightEntity(player, level, InteractionHand.MAIN_HAND,
                        captureTarget, new EntityHitResult(captureTarget, Vec3.ZERO));
                player.setShiftKeyDown(false);
                player.setPose(Pose.STANDING);
                if (interaction != InteractionResult.SUCCESS) {
                    throw new IllegalStateException("Entity capture interaction was not consumed: " + interaction);
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
                measuredWorkload = new AotakeNetworkSmokeWorkload(player);
                sparkProfile = ReflectiveSparkProfile.start(level.getServer());
                AotakeNetworkSmokeStatus.append("PASS spark-profiler-active");
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
            level.addFreshEntity(item);
            items.add(item);
        }
        return items;
    }

    private static boolean verifyConciseCommandRefresh(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        String root = CommonConfig.get().command().commandClearDrop();
        boolean registered = server.getCommands().getDispatcher().getRoot().getChild(root) != null;
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
                if (!enabled || !registered || ++commandRefreshSettleTicks < 10) return false;
                commandRefreshSettleTicks = 0;
                runConfigCommand(player, "concise.conciseClearDrop", false);
                commandRefreshStage = 2;
                return false;
            case 2:
                if (enabled || registered || ++commandRefreshSettleTicks < 10) return false;
                commandRefreshSettleTicks = 0;
                runConfigCommand(player, "concise.conciseClearDrop", true);
                commandRefreshStage = 3;
                return false;
            case 3:
                if (!enabled || !registered || ++commandRefreshSettleTicks < 10) return false;
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
        if (result <= 0) throw new IllegalStateException("Config command failed for " + path + '=' + value);
        commandRefreshWaitTicks = 0;
    }

    private static void runVerifyPhase(ServerPlayer player) {
        if (CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
            throw new IllegalStateException("Command config value was not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-command-config");
        PlayerSweepData data = PlayerSweepData.getData(player);
        if (data.isShowSweepResult() || data.isEnableWarningVoice()) {
            throw new IllegalStateException("Player preferences were not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-player-data");
        List<SimpleContainer> pages = WorldTrashData.get(player).getInventoryList();
        if (pages.isEmpty()) throw new IllegalStateException("Persisted dustbin page is missing");
        ItemStack stack = pages.get(0).getItem(0);
        if (stack.getItem() != Items.EMERALD || stack.getCount() != SENTINEL_COUNT) {
            throw new IllegalStateException("Persisted dustbin sentinel is missing: " + stack);
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data");
        if (AotakeNetworkSmokeWorkload.recoveredPaper() !=
                AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES * AotakeNetworkSmokeWorkload.PAPER_PER_CYCLE) {
            throw new IllegalStateException("Recovered cleanup payload did not survive restart");
        }
        if (AotakeNetworkSmokeWorkload.capturedCount(player) != AotakeNetworkSmokeProfilePlan.REQUIRED_CYCLES) {
            throw new IllegalStateException("Captured entities did not survive restart");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-measured-payload");
        xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeConfigs.verify(false);
        AotakeNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }

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

    /** Spark 的 Fabric 导出 API 没有跨版本承诺，烟测仅反射调用其原生 sampler。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ReflectiveSparkProfile {
        private final Object plugin;
        private final Object platform;
        private final Object sampler;
        private final Future<?> future;
        private final Path reportPath;
        private boolean written;

        private ReflectiveSparkProfile(Object plugin, Object platform, Object sampler, Future<?> future, Path reportPath) {
            this.plugin = plugin;
            this.platform = platform;
            this.sampler = sampler;
            this.future = future;
            this.reportPath = reportPath;
        }

        private static ReflectiveSparkProfile start(MinecraftServer server) {
            try {
                String configured = AotakeNetworkSmokeStatus.sparkReport();
                if (configured.isEmpty()) throw new IllegalStateException("Missing Spark report path");
                Object plugin = serverPlugin(server);
                Class<?> pluginBase = plugin.getClass().getSuperclass();
                Field platformField = pluginBase.getDeclaredField("platform");
                platformField.setAccessible(true);
                Object platform = platformField.get(plugin);
                Field threadDumperField = pluginBase.getDeclaredField("threadDumper");
                threadDumperField.setAccessible(true);
                Object threadDumper = threadDumperField.get(plugin);
                threadDumper.getClass().getMethod("ensureSetup").invoke(threadDumper);
                ClassLoader loader = platform.getClass().getClassLoader();
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 10.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, 60L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                builderType.getMethod("threadDumper", dumperType).invoke(builder,
                        plugin.getClass().getMethod("getDefaultThreadDumper").invoke(plugin));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", grouperType).invoke(builder, grouperType.getField("BY_POOL").get(null));
                Object sampler = method(builderType, "start", 0).invoke(builder);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveSparkProfile(plugin, platform, sampler, future, Paths.get(configured).toAbsolutePath());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start Spark sampler for network smoke", error);
            }
        }

        private boolean written() {
            return written;
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                ClassLoader loader = sampler.getClass().getClassLoader();
                Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
                Class<?> platformInfoType = Class.forName("me.lucko.spark.common.platform.PlatformInfo", true, loader);
                Class<?> commandSenderType = Class.forName("me.lucko.spark.common.command.sender.CommandSender", true, loader);
                Class<?> orderType = Class.forName("me.lucko.spark.common.sampler.ThreadNodeOrder", true, loader);
                Class<?> mergeType = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                Class<?> lookupType = Class.forName("me.lucko.spark.common.util.ClassSourceLookup", true, loader);
                Class<?> senderType = Class.forName("me.lucko.spark.fabric.FabricCommandSender", true, loader);
                Constructor<?> senderConstructor = senderType.getConstructors()[0];
                Object sender = senderConstructor.newInstance(BaniraServer.currentAs(MinecraftServer.class), plugin);
                Object props = propsType.getConstructor(platformInfoType, commandSenderType, java.util.Comparator.class,
                                String.class, mergeType, lookupType)
                        .newInstance(plugin.getClass().getMethod("getPlatformInfo").invoke(plugin), sender,
                                orderType.getField("BY_TIME").get(null), "Aotake network smoke", mergeMode(),
                                plugin.getClass().getMethod("createClassSourceLookup").invoke(plugin));
                Object proto = method(sampler.getClass(), "toProto", 1).invoke(sampler, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark report was empty");
                Files.createDirectories(reportPath.getParent());
                Files.write(reportPath, bytes);
                written = true;
                plugin.getClass().getMethod("disable").invoke(plugin);
                return true;
            } catch (ReflectiveOperationException | IOException error) {
                throw new IllegalStateException("Unable to write Spark report", error);
            }
        }

        private static Object serverPlugin(MinecraftServer server) throws ReflectiveOperationException {
            Class<?> modType = Class.forName("me.lucko.spark.fabric.FabricSparkMod");
            Field mod = modType.getDeclaredField("mod");
            mod.setAccessible(true);
            Class<?> pluginType = Class.forName("me.lucko.spark.fabric.plugin.FabricServerSparkPlugin");
            Object plugin = pluginType.getConstructor(modType, MinecraftServer.class).newInstance(mod.get(null), server);
            pluginType.getMethod("enable").invoke(plugin);
            return plugin;
        }

        private static Object mergeMode() {
            try {
                ClassLoader loader = ReflectiveSparkProfile.class.getClassLoader();
                Class<?> disambiguator = Class.forName("me.lucko.spark.common.util.MethodDisambiguator", true, loader);
                Class<?> merge = Class.forName("me.lucko.spark.common.sampler.node.MergeMode", true, loader);
                return merge.getMethod("sameMethod", disambiguator).invoke(null, disambiguator.getConstructor().newInstance());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark merge mode", error);
            }
        }

        private static Method method(Class<?> type, String name, int parameters) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) return candidate;
            }
            throw new IllegalStateException("Missing Spark method " + type.getName() + '#' + name);
        }
    }
}
