package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 自动连接独立服务端并验证真实网络包和容器同步。
 */
public final class AotakeNetworkSmokeClientRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(300);
    private static final long STATE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);
    private static final long UI_DURATION_NANOS = TimeUnit.SECONDS.toNanos(20);
    private static final long UI_CYCLE_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    private static AotakeNetworkSmokeClientRunner instance;

    private State state = State.CONNECT;
    private int ticks;
    private boolean disconnectRequested;
    private long startedAt;
    private long stateStartedAt;
    private final ServerSignals server = new ServerSignals();
    private final AotakeNetworkSmokeNotificationsCheck notifications = new AotakeNetworkSmokeNotificationsCheck();
    private AotakeNetworkSmokeScreens ui;
    private ReflectiveClientSparkProfile spark;
    private long uiStartedAt;
    private long lastUiCycleAt;
    private int uiCycles;

    private AotakeNetworkSmokeClientRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled() && instance == null) {
            instance = new AotakeNetworkSmokeClientRunner();
        }
    }

    public static void tick(@Nonnull Minecraft client) {
        if (instance != null) {
            instance.runTick(client);
        }
    }

    private void runTick(Minecraft client) {
        if (state == State.FINISHED) return;
        try {
            long now = System.nanoTime();
            if (startedAt == 0L) {
                startedAt = now;
                stateStartedAt = now;
                String phase = AotakeNetworkSmokeStatus.phase();
                if (!"phase-one".equals(phase) && !"phase-two".equals(phase)) {
                    throw new IllegalStateException("Unknown network smoke phase: " + phase);
                }
            }
            if (now - startedAt > TIMEOUT_NANOS || now - stateStartedAt > STATE_TIMEOUT_NANOS) {
                throw new IllegalStateException("Timed out in state " + state);
            }
            readServerStatus();
            if (state != State.CONNECT && state != State.LOGIN_SYNC && !disconnectRequested && !remote(client)) {
                throw new IllegalStateException("Remote connection lost in " + state);
            }
            ticks++;
            switch (state) {
                case CONNECT:
                    if (ticks >= 20) {
                        connect(client);
                    }
                    break;
                case LOGIN_SYNC:
                    waitForLoginSync(client);
                    break;
                case WAIT_C2S_READY:
                    waitForCustomChannel(client);
                    break;
                case CONFIG_ECHO:
                    waitForConfigEcho(client);
                    break;
                case DUSTBIN:
                    waitForDustbin(client);
                    break;
                case WAIT_SUSTAINED:
                    waitForSustainedWorkload(client);
                    break;
                case UI_DUSTBIN:
                    waitForUiDustbin(client);
                    break;
                case UI_WORKLOAD:
                    runUiWorkload(client);
                    break;
                case WAIT_SERVER:
                    waitForServerShutdown(client);
                    break;
                case FINISHED:
                    break;
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private void connect(Minecraft client) {
        if (client.getOverlay() != null) return;
        String host = System.getProperty("aotake.networkSmoke.host", "127.0.0.1");
        int port = Integer.getInteger("aotake.networkSmoke.port", 25575);
        ServerData server = new ServerData("Aotake Network Smoke", host + ":" + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString(server.ip), server, false, null);
        AotakeNetworkSmokeStatus.append("CONNECT " + host + ":" + port);
        transition(State.LOGIN_SYNC);
    }

    private static boolean remote(Minecraft client) {
        return client.player != null && client.level != null && client.getConnection() != null
                && client.getConnection().getConnection().isConnected()
                && !client.getConnection().getConnection().isMemoryConnection()
                && client.getSingleplayerServer() == null;
    }

    private void waitForLoginSync(Minecraft client) {
        if (!remote(client) || AotakeSweep.getClientServerTime().val() <= 0L) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS remote-login-sync");
        transition(State.WAIT_C2S_READY);
    }

    private void waitForCustomChannel(Minecraft client) {
        if (client.player == null || !AotakeNetworkSmokeClientPlan.isCustomChannelReady(
                ticks, PlayerUtils.isRemoteServerModInstalled(client.player, AotakeSweep.MODID))) {
            return;
        }
        if (!notifications.verifyWhenReady(client)) return;
        if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
            PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(false, false));
            transition(State.CONFIG_ECHO);
        } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
            if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
                throw new IllegalStateException("Persisted player preferences were not synchronized");
            }
            AotakeNetworkSmokeStatus.append("PASS persisted-player-data-client");
            PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            transition(State.DUSTBIN);
        } else {
            throw new IllegalStateException("Unknown network smoke phase: " + AotakeNetworkSmokeStatus.phase());
        }
    }

    private void waitForConfigEcho(Minecraft client) {
        if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS config-roundtrip");
        transition(State.WAIT_SUSTAINED);
    }

    private void waitForDustbin(Minecraft client) {
        if (!(client.screen instanceof ContainerScreen)
                || !DustbinRender.isDustbinTitle(client.screen.getTitle().getString())) {
            return;
        }
        ContainerScreen screen = (ContainerScreen) client.screen;
        if (client.player.containerMenu != screen.getMenu()) return;
        ItemStack stack = screen.getMenu().getSlot(0).getItem();
        if (stack.getItem() != Items.EMERALD || stack.getCount() != 7) {
            // The open-screen packet can arrive before its real slot synchronization. Keep the wall-clock bound.
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data-client");
        transition(State.WAIT_SERVER);
    }

    private void waitForSustainedWorkload(Minecraft client) {
        if (server.finished) throw new IllegalStateException("Server finished before client UI sampling");
        if (!server.sustainedReady || client.getOverlay() != null) return;
        PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
        AotakeNetworkSmokeStatus.append("SEND open-dustbin-ui");
        transition(State.UI_DUSTBIN);
    }

    private void waitForUiDustbin(Minecraft client) {
        if (server.finished) throw new IllegalStateException("Server finished before the UI dustbin opened");
        if (client.getOverlay() != null || !(client.screen instanceof ContainerScreen)
                || !DustbinRender.isDustbinTitle(client.screen.getTitle().getString())) return;
        ContainerScreen screen = (ContainerScreen) client.screen;
        if (client.player.containerMenu != screen.getMenu()) return;
        boolean populated = false;
        for (int index = 0; index < screen.getMenu().slots.size() - 36; index++) {
            if (!screen.getMenu().getSlot(index).getItem().isEmpty()) populated = true;
        }
        if (!populated) return;
        // This is the live recovery menu during workload, not the final persistence sentinel.
        AotakeNetworkSmokeStatus.append("PASS open-dustbin-ui");
        ui = new AotakeNetworkSmokeScreens(screen, () -> spark != null && !spark.future.isDone());
        ui.open(client);
        spark = ReflectiveClientSparkProfile.start();
        uiStartedAt = System.nanoTime();
        lastUiCycleAt = uiStartedAt;
        uiCycles = 1;
        AotakeNetworkSmokeStatus.append("PASS client-ui-spark-profiler-active");
        transition(State.UI_WORKLOAD);
    }

    private void runUiWorkload(Minecraft client) throws IOException {
        if (server.finished) throw new IllegalStateException("Server finished before raw client UI report was staged");
        long now = System.nanoTime();
        boolean ready = ui.readyForCycle(client);
        if (!spark.future.isDone() && ready && now - lastUiCycleAt >= UI_CYCLE_NANOS) {
            ui.runCycle(client, ++uiCycles);
            lastUiCycleAt = System.nanoTime();
        }
        if (spark.writeWhenComplete()) {
            readServerStatus();
            if (server.finished) throw new IllegalStateException("Server finished while staging raw client UI report");
            AotakeNetworkSmokeStatus.append("PASS client-ui-spark-report-written");
        }
        if (spark.future.isDone() && !ui.verified()) {
            throw new IllegalStateException("Client Spark sampling ended before 20 completed content cycles: " + ui.summary());
        }
        if (spark.written && now - uiStartedAt >= UI_DURATION_NANOS && ui.verified()) {
            AotakeNetworkSmokeStatus.append("PASS client-ui-sustained-workload cycles=" + ui.completedCycles()
                    + " requested-cycles=" + uiCycles + " duration-wall-ns=" + (now - uiStartedAt) + " " + ui.summary());
            ui.close(client);
            transition(State.WAIT_SERVER);
        }
    }

    private void waitForServerShutdown(Minecraft client) {
        if (!server.finished) {
            return;
        }
        if ("phase-one".equals(AotakeNetworkSmokeStatus.phase()) && !server.checkpointReady) {
            throw new IllegalStateException("Server finished phase-one without PASS final-checkpoint");
        }
        if (!disconnectRequested && client.getConnection() != null) {
            disconnectRequested = true;
            client.getConnection().getConnection().disconnect(Component.literal("Aotake network smoke complete"));
            return;
        }
        if (client.getConnection() != null && client.getConnection().getConnection().isConnected()) {
            return;
        }
        finish(client, AotakeNetworkSmokeStatus.phase());
    }

    private void readServerStatus() throws IOException {
        Path status = configuredPath("aotake.networkSmoke.serverStatus");
        if (!Files.isRegularFile(status)) return;
        server.accept(Files.readAllLines(status, StandardCharsets.UTF_8), AotakeNetworkSmokeStatus.phase());
    }

    private static Path configuredPath(String property) {
        String configured = System.getProperty(property, "").trim();
        if (configured.isEmpty()) throw new IllegalStateException("Missing " + property);
        return Paths.get(configured).toAbsolutePath();
    }

    private void transition(State next) {
        state = next;
        stateStartedAt = System.nanoTime();
        ticks = 0;
    }

    private void finish(Minecraft client, String phase) {
        xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeConfigs.verify(true);
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FINISHED " + phase);
        LOGGER.info("Aotake network smoke client finished {}", phase);
        client.stop();
    }

    private void fail(Minecraft client, String message) {
        state = State.FINISHED;
        if (ui != null) {
            try {
                ui.close(client);
            } catch (Throwable cleanupError) {
                LOGGER.warn("Unable to restore client smoke UI state", cleanupError);
            }
        }
        AotakeNetworkSmokeStatus.append("FAIL client " + message);
        LOGGER.error("Aotake network smoke client failed: {}", message);
        client.stop();
    }

    private enum State {
        CONNECT,
        LOGIN_SYNC,
        WAIT_C2S_READY,
        CONFIG_ECHO,
        DUSTBIN,
        WAIT_SUSTAINED,
        UI_DUSTBIN,
        UI_WORKLOAD,
        WAIT_SERVER,
        FINISHED
    }

    static final class ServerSignals {
        boolean sustainedReady;
        boolean checkpointReady;
        boolean finished;

        void accept(List<String> lines, String phase) {
            for (String line : lines) {
                if (line.startsWith("FAIL")) throw new IllegalStateException("Server reported " + line);
            }
            sustainedReady |= lines.contains("PASS sustained-ready");
            checkpointReady |= lines.stream().anyMatch(line -> line.equals("PASS final-checkpoint")
                    || line.startsWith("PASS final-checkpoint "));
            finished |= lines.contains("FINISHED " + phase);
        }
    }

    /** Spark 1.10 client plugin, Render thread only, native protobuf export. */
    private static final class ReflectiveClientSparkProfile {
        private final Object sampler;
        private final Future<?> future;
        private final Object platform;
        private final Object plugin;
        private final Path report;
        private boolean written;

        private ReflectiveClientSparkProfile(Object sampler, Future<?> future, Object platform, Object plugin, Path report) {
            this.sampler = sampler;
            this.future = future;
            this.platform = platform;
            this.plugin = plugin;
            this.report = report;
        }

        private static ReflectiveClientSparkProfile start() {
            try {
                Path report = configuredPath("aotake.networkSmoke.clientSparkReport");
                Object plugin = plugin();
                ClassLoader loader = plugin.getClass().getClassLoader();
                Field platformField = base(plugin).getDeclaredField("platform");
                platformField.setAccessible(true);
                Object platform = platformField.get(plugin);
                Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
                Object builder = builderType.getConstructor().newInstance();
                builderType.getMethod("samplingInterval", double.class).invoke(builder, 4.0D);
                builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, 20L, TimeUnit.SECONDS);
                builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
                Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
                Thread renderThread = Thread.currentThread();
                if (!"Render thread".equals(renderThread.getName())) {
                    throw new IllegalStateException("Client smoke is not running on Render thread");
                }
                Class<?> specific = Class.forName("me.lucko.spark.common.sampler.ThreadDumper$Specific", true, loader);
                Object dumper = specific.getConstructor(Thread.class).newInstance(renderThread);
                builderType.getMethod("threadDumper", dumperType).invoke(builder, dumper);
                Class<?> grouperType = Class.forName("me.lucko.spark.common.sampler.ThreadGrouper", true, loader);
                builderType.getMethod("threadGrouper", Supplier.class).invoke(builder, grouperType.getField("BY_POOL").get(null));
                // SamplerBuilder.start starts sampling; a second sampler.start would double the samples.
                Object sampler = method(builderType, "start", 1).invoke(builder, platform);
                Future<?> future = (Future<?>) method(sampler.getClass(), "getFuture", 0).invoke(sampler);
                return new ReflectiveClientSparkProfile(sampler, future, platform, plugin, report);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to start client Spark sampler", error);
            }
        }

        private boolean writeWhenComplete() {
            if (written || !future.isDone()) return false;
            try {
                future.get();
                ClassLoader loader = plugin.getClass().getClassLoader();
                Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
                Object props = propsType.getConstructor().newInstance();
                Class<?> senderData = Class.forName("me.lucko.spark.common.command.sender.CommandSender$Data", true, loader);
                propsType.getMethod("creator", senderData).invoke(props,
                        senderData.getConstructor(String.class, UUID.class).newInstance("Aotake client UI smoke", null));
                propsType.getMethod("comment", String.class).invoke(props, "Aotake client UI smoke");
                Class<?> strategyType = Class.forName("me.lucko.spark.common.sampler.java.MergeStrategy", true, loader);
                propsType.getMethod("mergeStrategy", strategyType).invoke(props, strategyType.getField("SAME_METHOD").get(null));
                propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, (Supplier<Object>) this::classSourceLookup);
                Object proto = method(sampler.getClass(), "toProto", 2).invoke(sampler, platform, props);
                byte[] bytes = (byte[]) proto.getClass().getMethod("toByteArray").invoke(proto);
                if (bytes.length == 0) throw new IllegalStateException("Client Spark report was empty");
                Files.createDirectories(report.getParent());
                Files.write(report, bytes);
                written = true;
                return true;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted writing client Spark report", error);
            } catch (ReflectiveOperationException | IOException | java.util.concurrent.ExecutionException error) {
                throw new IllegalStateException("Unable to write client Spark report", error);
            }
        }

        private Object classSourceLookup() {
            try {
                return base(plugin).getMethod("createClassSourceLookup").invoke(plugin);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unable to create Spark class source lookup", error);
            }
        }

        private static Object plugin() throws ReflectiveOperationException {
            Field listeners = NeoForge.EVENT_BUS.getClass().getDeclaredField("listeners");
            listeners.setAccessible(true);
            for (Object candidate : ((Map<?, ?>) listeners.get(NeoForge.EVENT_BUS)).keySet()) {
                if (candidate != null && candidate.getClass().getName()
                        .equals("me.lucko.spark.neoforge.plugin.NeoForgeClientSparkPlugin")) return candidate;
            }
            throw new IllegalStateException("Spark client plugin was not registered");
        }

        private static Class<?> base(Object plugin) {
            Class<?> type = plugin.getClass();
            while (type != null && !type.getName().equals("me.lucko.spark.neoforge.plugin.NeoForgeSparkPlugin")) type = type.getSuperclass();
            if (type == null) throw new IllegalStateException("Spark base plugin was not found");
            return type;
        }

        private static Method method(Class<?> type, String name, int parameters) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) return candidate;
            }
            throw new IllegalStateException("Missing Spark method " + type.getName() + '#' + name);
        }
    }
}
