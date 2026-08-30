package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.enums.EnumChunkCheckMode;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.api.event.BaniraEvents;

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

/** 在真实专服 tick 链路中覆盖倒计时、区块清理、实体捕获及玩家数据同步。 */
public final class AotakeNetworkSmokeServerRunner {
    private static final int TIMEOUT_TICKS = 1000;
    private static boolean ready;
    private static boolean finished;
    private static int shutdownTicks;
    private static int gameplayTicks;
    private static Step step = Step.PREPARE;
    private static ItemEntity countdownItem;
    private static List<ItemEntity> chunkItems = Collections.emptyList();
    private static Cow captureTarget;
    private static ReflectiveSparkProfile sparkProfile;

    private AotakeNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) BaniraEvents.Server.onTick(event -> onServerTick());
    }

    private static void onServerTick() {
        try {
            MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
            if (server == null || !server.isRunning()) return;
            if (sparkProfile != null && sparkProfile.writeWhenComplete()) {
                AotakeNetworkSmokeStatus.append("PASS spark-report-written");
            }
            if (finished) {
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
                if (!runGameplay(player)) return;
                if (!playerPreferencesReceived(player)) return;
                AotakeNetworkSmokeStatus.append("PASS player-config-roundtrip");
                finish();
            } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
                if (!playerPreferencesReceived(player)) {
                    throw new IllegalStateException("Player sweep preferences were not restored from disk");
                }
                AotakeNetworkSmokeStatus.append("PASS persisted-player-config");
                finish();
            } else {
                throw new IllegalStateException("Unknown network smoke phase " + AotakeNetworkSmokeStatus.phase());
            }
        } catch (Throwable error) {
            finished = true;
            AotakeNetworkSmokeStatus.append("FAIL server " + error);
            throw error;
        }
    }

    private static boolean runGameplay(ServerPlayer player) {
        if (++gameplayTicks > TIMEOUT_TICKS) throw new IllegalStateException("Gameplay smoke timed out in " + step);
        ServerLevel level = player.serverLevel();
        switch (step) {
            case PREPARE:
                configureFixture();
                sparkProfile = ReflectiveSparkProfile.start();
                AotakeNetworkSmokeStatus.append("PASS spark-profiler-active");
                countdownItem = new ItemEntity(level, 8.5D, 65.0D, 8.5D, new ItemStack(Items.DIAMOND));
                level.addFreshEntity(countdownItem);
                EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1L);
                AotakeNetworkSmokeStatus.append("START countdown-cleanup");
                step = Step.WAIT_COUNTDOWN;
                return false;
            case WAIT_COUNTDOWN:
                if (countdownItem.isAlive()) return false;
                AotakeNetworkSmokeStatus.append("PASS countdown-cleanup");
                CommonConfig.get().base().sweep().sweepInterval(TimeUnit.HOURS.toMillis(1));
                List<ItemEntity> items = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    ItemEntity item = new ItemEntity(level, 16.5D + i * 0.1D, 65.0D, 16.5D, new ItemStack(Items.GOLD_INGOT));
                    level.addFreshEntity(item);
                    items.add(item);
                }
                chunkItems = items;
                AotakeNetworkSmokeStatus.append("START chunk-cleanup");
                step = Step.WAIT_CHUNK;
                return false;
            case WAIT_CHUNK:
                if (chunkItems.stream().anyMatch(Entity::isAlive)) return false;
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
                InteractionResult result = EventHandlerProxy.onRightEntity(player, level, InteractionHand.MAIN_HAND, captureTarget, null);
                player.setShiftKeyDown(false);
                player.setPose(Pose.STANDING);
                if (result != InteractionResult.SUCCESS) throw new IllegalStateException("Entity capture interaction was not consumed");
                AotakeNetworkSmokeStatus.append("START entity-capture");
                step = Step.WAIT_CAPTURE;
                return false;
            case WAIT_CAPTURE:
                if (captureTarget.isAlive()) return false;
                boolean captured = player.getInventory().items.stream().anyMatch(stack -> AotakeUtils.hasAotakeTag(stack));
                if (!captured) throw new IllegalStateException("Captured entity payload was not added to inventory");
                AotakeNetworkSmokeStatus.append("PASS entity-capture");
                step = Step.COMPLETE;
                return true;
            case COMPLETE:
                return true;
            default:
                throw new IllegalStateException("Unknown gameplay step " + step);
        }
    }

    private static void configureFixture() {
        CommonConfig.BaseView base = CommonConfig.get().base();
        base.sweep().sweepWhenNoPlayer(true).sweepInterval(50L)
                .entityList(Collections.singletonList("minecraft:item"))
                .entityListMode(EnumListType.BLACK).entityListLimit(Integer.MAX_VALUE);
        base.chunk().chunkCheckInterval(1L).chunkCheckLimit(2).chunkCheckRetain(0.0D)
                .chunkCheckNotice(false).chunkCheckMode(EnumChunkCheckMode.DEFAULT)
                .chunkCheckEntityList(Collections.singletonList("minecraft:item"))
                .chunkCheckEntityListMode(EnumListType.BLACK).chunkCheckOnlyNotice(false)
                .chunkVaultEnabled(false);
        base.entityCatch().allowCatchEntity(true).catchItem(Collections.singletonList("minecraft:stick"));
        base.batch().sweepEntityLimit(128).sweepEntityInterval(1).sweepBatchLimit(8);
    }

    private static boolean playerPreferencesReceived(ServerPlayer player) {
        PlayerSweepData data = PlayerSweepData.getData(player);
        return !data.isShowSweepResult() && !data.isEnableWarningVoice();
    }

    private static void finish() {
        AotakeNetworkSmokeStatus.append("FINISHED " + AotakeNetworkSmokeStatus.phase());
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

    /** Spark 对外未提供稳定的跨加载器导出 API，开发烟测仅通过反射写其原生报告格式。 */
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
                String configured = System.getProperty("aotake.networkSmoke.sparkReport", "").trim();
                if (configured.isEmpty()) throw new IllegalStateException("Missing aotake.networkSmoke.sparkReport");
                Object plugin = serverPlugin();
                Field platformField = plugin.getClass().getSuperclass().getDeclaredField("platform");
                platformField.setAccessible(true);
                Object platform = platformField.get(plugin);
                ClassLoader loader = platform.getClass().getClassLoader();
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                Class<?> modeType = Class.forName("me.lucko.spark.common.sampler.SamplerMode", true, loader);
                Object mode = Enum.valueOf((Class) modeType, "EXECUTION");
                builderType.getMethod("mode", modeType).invoke(builder, mode);
                builderType.getMethod("samplingInterval", double.class).invoke(builder,
                        ((Number) modeType.getMethod("defaultInterval").invoke(mode)).doubleValue());
                builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, 20L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                builderType.getMethod("threadDumper", dumperType).invoke(builder, plugin.getClass().getMethod("getDefaultThreadDumper").invoke(plugin));
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", Supplier.class).invoke(builder, grouperType.getField("BY_POOL").get(null));
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                Object container = platform.getClass().getMethod("getSamplerContainer").invoke(platform);
                method(container.getClass(), "setActiveSampler", 1).invoke(container, sampler);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveSparkProfile(platform, sampler, future, Paths.get(configured).toAbsolutePath());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start Spark sampler for network smoke", error);
            }
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                ClassLoader loader = sampler.getClass().getClassLoader();
                Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
                Object props = propsType.getConstructor().newInstance();
                Class<?> senderType = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data", true, loader);
                propsType.getMethod("creator", senderType).invoke(props, senderType.getConstructor(String.class, java.util.UUID.class).newInstance("Aotake network smoke", null));
                configureMergeStrategy(propsType, props, loader);
                propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, (Supplier<Object>) () -> classSourceLookup(platform));
                Object proto = method(sampler.getClass(), "toProto", 2).invoke(sampler, platform, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Spark profile was empty");
                Files.createDirectories(reportPath.getParent());
                Files.write(reportPath, bytes);
                written = true;
                return true;
            } catch (ReflectiveOperationException | java.io.IOException error) {
                throw new IllegalStateException("Unable to write Spark report", error);
            }
        }

        private static Object serverPlugin() throws ReflectiveOperationException {
            Class<?> modType = Class.forName("me.lucko.spark.fabric.FabricSparkMod");
            Field mod = modType.getDeclaredField("mod"); mod.setAccessible(true);
            Field plugin = modType.getDeclaredField("activeServerPlugin"); plugin.setAccessible(true);
            return plugin.get(mod.get(null));
        }

        private static Method method(Class<?> type, String name, int params) {
            for (Method candidate : type.getMethods()) if (candidate.getName().equals(name) && candidate.getParameterCount() == params) return candidate;
            throw new IllegalStateException("Missing Spark method " + type.getName() + '#' + name);
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

        private static void configureMergeStrategy(Class<?> propsType, Object props, ClassLoader loader)
                throws ReflectiveOperationException {
            try {
                Class<?> strategyType = Class.forName("me.lucko.spark.common.sampler.java.MergeStrategy", true, loader);
                propsType.getMethod("mergeStrategy", strategyType)
                        .invoke(props, strategyType.getField("SAME_METHOD").get(null));
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                propsType.getMethod("mergeMode", Supplier.class)
                        .invoke(props, (Supplier<Object>) ReflectiveSparkProfile::mergeMode);
            }
        }

        private static Object classSourceLookup(Object platform) {
            try {
                return platform.getClass().getMethod("createClassSourceLookup").invoke(platform);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark class source lookup", error);
            }
        }
    }

    private enum Step { PREPARE, WAIT_COUNTDOWN, WAIT_CHUNK, WAIT_CAPTURE, COMPLETE }
}
