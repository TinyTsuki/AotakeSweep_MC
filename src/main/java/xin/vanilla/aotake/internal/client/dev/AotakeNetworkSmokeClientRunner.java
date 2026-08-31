package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.common.util.PacketUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** 自动连接独立服务端，并验证玩家偏好与垃圾箱容器同步。 */
public final class AotakeNetworkSmokeClientRunner {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int TIMEOUT_TICKS = 1200;

    private static AotakeNetworkSmokeClientRunner instance;

    private State state = State.CONNECT;
    private int ticks;

    private AotakeNetworkSmokeClientRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) {
            instance = new AotakeNetworkSmokeClientRunner();
        }
    }

    public static void tick(Minecraft client) {
        if (instance != null) {
            instance.runTick(client);
        }
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
                    waitForLoginSync(client);
                    break;
                case CONFIG_ECHO:
                    waitForConfigEcho(client);
                    break;
                case DUSTBIN:
                    waitForDustbin(client);
                    break;
                case WAIT_SERVER:
                    waitForServer(client);
                    break;
                case FINISHED:
                    break;
                default:
                    throw new IllegalStateException("Unknown smoke client state " + state);
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private void connect(Minecraft client) {
        String host = System.getProperty("aotake.networkSmoke.host", "127.0.0.1");
        int port = Integer.getInteger("aotake.networkSmoke.port", 25575);
        ServerData server = new ServerData("Aotake Network Smoke", host + ':' + port, false);
        client.setCurrentServer(server);
        ConnectScreen.startConnecting(client.screen, client, ServerAddress.parseString(server.ip), server);
        AotakeNetworkSmokeStatus.append("CONNECT " + host + ':' + port);
        state = State.LOGIN_SYNC;
        ticks = 0;
    }

    private void waitForLoginSync(Minecraft client) {
        if (client.player == null || client.level == null || client.getSingleplayerServer() != null
                || AotakeSweep.getClientServerTime().val() <= 0L) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS remote-login-sync");
        AotakeNetworkSmokeClientPlan.LoginAction action = AotakeNetworkSmokeClientPlan.afterLogin(
                AotakeNetworkSmokeStatus.phase(), AotakeSweep.isClientCachedShowSweepResult(),
                AotakeSweep.isClientCachedEnableWarningVoice());
        if (action == AotakeNetworkSmokeClientPlan.LoginAction.SEND_PLAYER_CONFIG) {
            PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(false, false));
            state = State.CONFIG_ECHO;
        } else {
            AotakeNetworkSmokeStatus.append("PASS persisted-player-data-client");
            PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            state = State.DUSTBIN;
        }
        ticks = 0;
    }

    private void waitForConfigEcho(Minecraft client) {
        if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS config-roundtrip");
        state = State.WAIT_SERVER;
        ticks = 0;
    }

    private void waitForDustbin(Minecraft client) {
        if (!(client.screen instanceof ContainerScreen)) {
            return;
        }
        ContainerScreen screen = (ContainerScreen) client.screen;
        ItemStack stack = screen.getMenu().getSlot(0).getItem();
        if (stack.getItem() != Items.EMERALD || stack.getCount() != 7) {
            throw new IllegalStateException("Synchronized dustbin sentinel is missing: " + stack);
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data-client");
        state = State.WAIT_SERVER;
        ticks = 0;
    }

    private void waitForServer(Minecraft client) {
        if (!serverFinished()) {
            return;
        }
        finish(client);
    }

    private boolean serverFinished() {
        String configured = System.getProperty("aotake.networkSmoke.serverStatus", "").trim();
        if (configured.isEmpty()) {
            return false;
        }
        try {
            String status = new String(Files.readAllBytes(Paths.get(configured)), StandardCharsets.UTF_8);
            return AotakeNetworkSmokeClientPlan.serverFinished(status, AotakeNetworkSmokeStatus.phase());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void finish(Minecraft client) {
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FINISHED " + AotakeNetworkSmokeStatus.phase());
        LOGGER.info("Aotake network smoke client finished {}", AotakeNetworkSmokeStatus.phase());
        client.stop();
    }

    private void fail(Minecraft client, String message) {
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FAIL client " + message);
        LOGGER.error("Aotake network smoke client failed: {}", message);
        client.stop();
    }

    private enum State {
        CONNECT,
        LOGIN_SYNC,
        CONFIG_ECHO,
        DUSTBIN,
        WAIT_SERVER,
        FINISHED
    }
}
