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
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.api.event.BaniraEvents;
import xin.vanilla.banira.common.util.CommandUtils;

import java.io.IOException;
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
import java.util.function.Supplier;

/** 真实专服内验证 Aotake 的玩法链、持久化与 Spark 原生报告。 */
public final class AotakeNetworkSmokeServerRunner {
    private static final int SENTINEL_COUNT = 7;
    private static final int SENTINEL_CONFIG_VALUE = 11;
    private static final int WORKLOAD_FILTER_RUNS_PER_TICK = 8;

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
    private static Cow captureTarget;
    private static ReflectiveSparkProfile sparkProfile;
    private static AotakeNetworkSmokeWorkload workload;
    private static int workloadObservedEntities;

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
            boolean result = CommandUtils.executeCommand(player,
                    "aotake config common base.batch.sweepBatchLimit " + SENTINEL_CONFIG_VALUE, 4, true);
            if (!result || CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
                throw new IllegalStateException("Config command did not update sweepBatchLimit");
            }
            AotakeNetworkSmokeStatus.append("PASS config-command-roundtrip");
            commandVerified = true;
        }
        PlayerSweepData data = PlayerSweepData.getData(player);
        if (data.isShowSweepResult() || data.isEnableWarningVoice()) return;
        WorldTrashData.getTrashContainer(player, 1);
        SimpleContainer inventory = WorldTrashData.get(player).getInventoryList().get(0);
        inventory.setItem(0, new ItemStack(Items.EMERALD, SENTINEL_COUNT));
        WorldTrashData.get(player).setDirty();
        AotakeNetworkSmokeStatus.append("PASS server-config-roundtrip");
        AotakeNetworkSmokeStatus.append("PASS phase-one-world-write");
        AotakeNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    private static boolean runGameplay(ServerPlayer player) {
        if (++gameplayTicks > 1200) {
            throw new IllegalStateException("Gameplay smoke timed out in " + gameplayStep);
        }
        ServerLevel level = (ServerLevel) player.level();
        switch (gameplayStep) {
            case PREPARE:
                configureGameplayFixture();
                sparkProfile = ReflectiveSparkProfile.start();
                AotakeNetworkSmokeStatus.append("PASS spark-profiler-active");
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
                captureTarget = EntityType.COW.create(level);
                if (captureTarget == null) throw new IllegalStateException("Could not create capture target");
                captureTarget.moveTo(24.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                level.addFreshEntity(captureTarget);
                player.teleportTo(level, 23.5D, 65.0D, 24.5D, 0.0F, 0.0F);
                player.getInventory().add(new ItemStack(Items.STICK));
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
                if (player.getInventory().items.stream().noneMatch(AotakeUtils::hasAotakeTag)) {
                    throw new IllegalStateException("Captured entity payload was not added to inventory");
                }
                AotakeNetworkSmokeStatus.append("PASS entity-capture");
                workload = new AotakeNetworkSmokeWorkload(gameplayTicks);
                AotakeNetworkSmokeStatus.append("START sustained-gameplay-workload");
                gameplayStep = GameplayStep.SUSTAINED;
                return false;
            case SUSTAINED:
                if (!runSustainedGameplay(player)) return false;
                AotakeNetworkSmokeStatus.append("PASS sustained-gameplay-workload");
                gameplayStep = GameplayStep.COMPLETE;
                return true;
            case COMPLETE:
                return true;
            default:
                throw new IllegalStateException("Unknown gameplay step " + gameplayStep);
        }
    }

    private static boolean runSustainedGameplay(ServerPlayer player) {
        if (workload == null) throw new IllegalStateException("Missing sustained gameplay workload");
        ServerLevel level = (ServerLevel) player.level();
        for (int index = 0; index < WORKLOAD_FILTER_RUNS_PER_TICK; index++) {
            workloadObservedEntities += AotakeUtils.getAllEntitiesByFilter(null, true).size();
        }
        if (workload.shouldTriggerSweepAt(gameplayTicks)) {
            for (int index = 0; index < 6; index++) {
                level.addFreshEntity(new ItemEntity(level, fixtureX(player) + index * 0.1D,
                        fixtureY(player), fixtureZ(player), new ItemStack(Items.IRON_NUGGET)));
            }
            EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
        }
        if (workload.shouldCaptureAt(gameplayTicks)) {
            Cow target = EntityType.COW.create(level);
            if (target == null) throw new IllegalStateException("Could not create sustained capture target");
            target.moveTo(fixtureX(player), fixtureY(player), fixtureZ(player), 0.0F, 0.0F);
            level.addFreshEntity(target);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setShiftKeyDown(true);
            player.setPose(Pose.CROUCHING);
            InteractionResult interaction = EventHandlerProxy.onRightEntity(player, level, InteractionHand.MAIN_HAND,
                    target, new EntityHitResult(target, Vec3.ZERO));
            player.setShiftKeyDown(false);
            player.setPose(Pose.STANDING);
            if (interaction != InteractionResult.SUCCESS) {
                throw new IllegalStateException("Sustained capture interaction was not consumed: " + interaction);
            }
        }
        if (!workload.completeAt(gameplayTicks)) return false;
        if (workloadObservedEntities <= 0) throw new IllegalStateException("Sustained entity filtering observed no entities");
        return true;
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

    private static double fixtureX(ServerPlayer player) {
        return (player.blockPosition().getX() >> 4) * 16 + 8.5D;
    }

    private static double fixtureY(ServerPlayer player) {
        return player.getY() + 2.0D;
    }

    private static double fixtureZ(ServerPlayer player) {
        return (player.blockPosition().getZ() >> 4) * 16 + 8.5D;
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
        boolean result = CommandUtils.executeCommand(player,
                "aotake config common " + path + " " + value, 4, true);
        if (!result) throw new IllegalStateException("Config command failed for " + path + '=' + value);
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
        WAIT_CAPTURE,
        SUSTAINED,
        COMPLETE
    }

    /** Spark 的 Fabric 导出 API 没有跨版本承诺，烟测仅反射调用其原生 sampler。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class ReflectiveSparkProfile {
        private final Object platform;
        private final Object sampler;
        private final Future<?> future;
        private final Path reportPath;
        private boolean written;

        private ReflectiveSparkProfile(Object platform, Object sampler, Future<?> future, Path reportPath) {
            this.platform = platform;
            this.sampler = sampler;
            this.future = future;
            this.reportPath = reportPath;
        }

        private static ReflectiveSparkProfile start() {
            try {
                String configured = AotakeNetworkSmokeStatus.sparkReport();
                if (configured.isEmpty()) throw new IllegalStateException("Missing Spark report path");
                Object platform = getPlatform();
                Object plugin = getServerPlugin();
                ClassLoader loader = platform.getClass().getClassLoader();
                Object samplerContainer = platform.getClass().getMethod("getSamplerContainer").invoke(platform);
                method(samplerContainer.getClass(), "stopActiveSampler", 1).invoke(samplerContainer, true);
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                Class<?> modeType = Class.forName("me.lucko.spark.common.sampler.SamplerMode", true, loader);
                Object executionMode = Enum.valueOf((Class) modeType, "EXECUTION");
                double interval = ((Number) modeType.getMethod("defaultInterval").invoke(executionMode)).doubleValue();
                builderType.getMethod("mode", modeType).invoke(builder, executionMode);
                builderType.getMethod("samplingInterval", double.class).invoke(builder, interval);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, 20L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                builderType.getMethod("threadDumper", dumperType).invoke(builder,
                        plugin.getClass().getMethod("getDefaultThreadDumper").invoke(plugin));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", Supplier.class).invoke(builder,
                        grouperType.getField("BY_POOL").get(null));
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                method(samplerContainer.getClass(), "setActiveSampler", 1).invoke(samplerContainer, sampler);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveSparkProfile(platform, sampler, future, Paths.get(configured).toAbsolutePath());
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
                Object props = propsType.getConstructor().newInstance();
                Class<?> senderDataType = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data", true, loader);
                Object creator = senderDataType.getConstructor(String.class, java.util.UUID.class)
                        .newInstance("Aotake network smoke", null);
                propsType.getMethod("creator", senderDataType).invoke(props, creator);
                Supplier<Object> classSourceLookup = () -> invokeClassSourceLookup(platform);
                Class<?> mergeStrategyType = Class.forName("me.lucko.spark.common.sampler.java.MergeStrategy", true, loader);
                propsType.getMethod("mergeStrategy", mergeStrategyType).invoke(props,
                        mergeStrategyType.getField("SAME_METHOD").get(null));
                propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, classSourceLookup);
                Object proto = method(sampler.getClass(), "toProto", 2).invoke(sampler, platform, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark report was empty");
                Files.createDirectories(reportPath.getParent());
                Files.write(reportPath, bytes);
                written = true;
                return true;
            } catch (ReflectiveOperationException | IOException error) {
                throw new IllegalStateException("Unable to write Spark report", error);
            }
        }

        private static Object getPlatform() throws ReflectiveOperationException {
            Object plugin = getServerPlugin();
            Field platformField = plugin.getClass().getSuperclass().getDeclaredField("platform");
            platformField.setAccessible(true);
            return platformField.get(plugin);
        }

        private static Object getServerPlugin() throws ReflectiveOperationException {
            Class<?> modType = Class.forName("me.lucko.spark.fabric.FabricSparkMod");
            Field mod = modType.getDeclaredField("mod");
            mod.setAccessible(true);
            Field plugin = modType.getDeclaredField("activeServerPlugin");
            plugin.setAccessible(true);
            return plugin.get(mod.get(null));
        }

        private static Object invokeClassSourceLookup(Object platform) {
            try {
                return platform.getClass().getMethod("createClassSourceLookup").invoke(platform);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark class source lookup", error);
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
