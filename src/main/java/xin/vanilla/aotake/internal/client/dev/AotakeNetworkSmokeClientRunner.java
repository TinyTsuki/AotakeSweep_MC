package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.common.util.PacketUtils;

/** 自动加入独立服务端并验证 Aotake 玩家偏好同步。 */
public final class AotakeNetworkSmokeClientRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TIMEOUT_TICKS = 1400;
    private static final int SERVER_SETTLE_TICKS = 620;
    private static AotakeNetworkSmokeClientRunner instance;

    private State state = State.CONNECT;
    private int ticks;

    private AotakeNetworkSmokeClientRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) instance = new AotakeNetworkSmokeClientRunner();
    }

    public static void tick(Minecraft client) {
        if (instance != null) instance.runTick(client);
    }

    private void runTick(Minecraft client) {
        if (++ticks > TIMEOUT_TICKS) {
            fail(client, "Timed out in state " + state);
            return;
        }
        try {
            switch (state) {
                case CONNECT:
                    if (ticks >= 20) connect(client);
                    break;
                case LOGIN_SYNC:
                    awaitLoginSync(client);
                    break;
                case CONFIG_ECHO:
                    awaitConfigEcho(client);
                    break;
                case SERVER_SETTLE:
                    if (ticks >= SERVER_SETTLE_TICKS) finish(client);
                    break;
                default:
                    break;
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private void connect(Minecraft client) {
        String host = System.getProperty("aotake.networkSmoke.host", "127.0.0.1");
        int port = Integer.getInteger("aotake.networkSmoke.port", 25577);
        ServerData server = new ServerData("Aotake Network Smoke", host + ':' + port, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString(server.ip), server, false, null);
        AotakeNetworkSmokeStatus.append("CONNECT " + host + ':' + port);
        state = State.LOGIN_SYNC;
        ticks = 0;
    }

    private void awaitLoginSync(Minecraft client) {
        if (client.player == null || client.level == null || client.getSingleplayerServer() != null
                || AotakeSweep.getClientServerTime().value() == 0L) return;
        AotakeNetworkSmokeStatus.append("PASS remote-login-sync");
        if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
            PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(false, false));
            state = State.CONFIG_ECHO;
        } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
            if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
                throw new IllegalStateException("Persisted player preferences were not synchronized");
            }
            AotakeNetworkSmokeStatus.append("PASS persisted-player-config-client");
            finish(client);
        } else {
            throw new IllegalStateException("Unknown network smoke phase " + AotakeNetworkSmokeStatus.phase());
        }
        ticks = 0;
    }

    private void awaitConfigEcho(Minecraft client) {
        if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) return;
        AotakeNetworkSmokeStatus.append("PASS player-config-roundtrip");
        state = State.SERVER_SETTLE;
        ticks = 0;
    }

    private void finish(Minecraft client) {
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FINISHED " + AotakeNetworkSmokeStatus.phase());
        LOGGER.info("Aotake network smoke client finished {}", AotakeNetworkSmokeStatus.phase());
        client.stop();
    }

    private void fail(Minecraft client, String reason) {
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FAIL client " + reason);
        LOGGER.error("Aotake network smoke client failed: {}", reason);
        client.stop();
    }

    private enum State { CONNECT, LOGIN_SYNC, CONFIG_ECHO, SERVER_SETTLE, FINISHED }
}
