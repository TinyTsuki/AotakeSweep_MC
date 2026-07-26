package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.PlayerConfigSyncToServer;
import xin.vanilla.banira.common.util.PacketUtils;

import javax.annotation.Nonnull;

/**
 * 自动连接独立服务端并验证真实网络包和容器同步。
 */
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

    public static void tick(@Nonnull Minecraft client) {
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
                    if (ticks >= 20) {
                        connect(client);
                    }
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
                case FINISHED:
                    break;
            }
        } catch (Throwable error) {
            fail(client, error.toString());
        }
    }

    private void connect(Minecraft client) {
        String host = System.getProperty("aotake.networkSmoke.host", "127.0.0.1");
        int port = Integer.getInteger("aotake.networkSmoke.port", 25575);
        ServerData server = new ServerData("Aotake Network Smoke", host + ":" + port, false);
        client.setCurrentServer(server);
        client.setScreen(new ConnectingScreen(client.screen, client, server));
        AotakeNetworkSmokeStatus.append("CONNECT " + host + ":" + port);
        state = State.LOGIN_SYNC;
        ticks = 0;
    }

    private void waitForLoginSync(Minecraft client) {
        if (client.player == null || client.level == null || client.getSingleplayerServer() != null
                || AotakeSweep.getClientServerTime().val() <= 0L) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS remote-login-sync");
        if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
            PacketUtils.sendPacketToServer(new PlayerConfigSyncToServer(false, false));
            state = State.CONFIG_ECHO;
        } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
            if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
                throw new IllegalStateException("Persisted player preferences were not synchronized");
            }
            AotakeNetworkSmokeStatus.append("PASS persisted-player-data-client");
            PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            state = State.DUSTBIN;
        } else {
            throw new IllegalStateException("Unknown network smoke phase: " + AotakeNetworkSmokeStatus.phase());
        }
        ticks = 0;
    }

    private void waitForConfigEcho(Minecraft client) {
        if (AotakeSweep.isClientCachedShowSweepResult() || AotakeSweep.isClientCachedEnableWarningVoice()) {
            return;
        }
        AotakeNetworkSmokeStatus.append("PASS config-roundtrip");
        finish(client, "phase-one");
    }

    private void waitForDustbin(Minecraft client) {
        if (!(client.screen instanceof ContainerScreen)) {
            return;
        }
        ContainerScreen<?> screen = (ContainerScreen<?>) client.screen;
        ItemStack stack = screen.getMenu().getSlot(0).getItem();
        if (stack.getItem() != Items.DIAMOND || stack.getCount() != 7) {
            throw new IllegalStateException("Synchronized dustbin sentinel is missing: " + stack);
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data-client");
        finish(client, "phase-two");
    }

    private void finish(Minecraft client, String phase) {
        state = State.FINISHED;
        AotakeNetworkSmokeStatus.append("FINISHED " + phase);
        LOGGER.info("Aotake network smoke client finished {}", phase);
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
        FINISHED
    }
}
