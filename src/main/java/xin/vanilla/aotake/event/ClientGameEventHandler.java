package xin.vanilla.aotake.event;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.internal.client.dev.AotakeNetworkSmokeClientRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudRenderEvent;
import xin.vanilla.banira.api.client.hud.HudOverlayElement;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；通过 Banira 客户端事件门面订阅。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static long lastTime = 0;
    private static boolean showProgress = false;
    private static final xin.vanilla.aotake.client.ui.SweepProgressUiBinding.KeyState PROGRESS_STATE =
            new xin.vanilla.aotake.client.ui.SweepProgressUiBinding.KeyState();

    private ClientGameEventHandler() {
    }

    public static void register() {
        xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.register();
        BaniraClientEvents.ModLifecycle.onClientSetup(event -> ProgressRender.register());
        xin.vanilla.banira.api.client.event.BaniraClientEvents.Player.onClientLoggedIn(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.beginConnection());
        xin.vanilla.banira.api.client.event.BaniraClientEvents.Player.onClientLoggedOut(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.reset());
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEvents.Player.onClientLoggedOut(player -> {
            PROGRESS_STATE.reset();
            showProgress = false;
            ProgressRender.reset();
        });
        BaniraClientEvents.Client.onClientTick(event -> onClientTick());
        AotakeNetworkSmokeClientRunner.register();
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_BAR, ClientGameEventHandler::interceptExperience);
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_TEXT, ClientGameEventHandler::interceptExperience);
    }

    private static void onClientTick() {
        AotakeUiSmokeRunner.tick(Minecraft.getInstance());
        AotakeNetworkSmokeClientRunner.tick(Minecraft.getInstance());
        showProgress = PROGRESS_STATE.tick(ClientModEventHandler.PROGRESS_KEY.isDown(), Minecraft.getInstance().screen != null,
                ClientConfig.get().progressBar().progressBarKeyApplyMode());
        if (Minecraft.getInstance().screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                lastTime = System.currentTimeMillis();
                PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            }
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (ProgressRender.shouldRenderProgressOverlay(showProgress)) {
            ProgressRender.render(graphics, showProgress);
        } else ProgressRender.reset();
    }

    private static void interceptExperience(BaniraHudRenderEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        if (ProgressRender.shouldRenderProgressOverlay(showProgress)
                && ProgressRender.shouldHideVanillaExperienceBar(showProgress)) {
            event.cancel();
        }
    }
}
