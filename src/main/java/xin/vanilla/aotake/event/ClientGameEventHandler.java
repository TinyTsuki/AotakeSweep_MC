package xin.vanilla.aotake.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeNetworkSmokeClientRunner;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.hud.BaniraHudEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudRenderEvent;
import xin.vanilla.banira.api.client.hud.HudOverlayElement;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；通过 {@link BaniraClientEvents} 订阅，在 {@link xin.vanilla.aotake.client.AotakeClientBootstrap} 中注册。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static long lastTime = 0;
    private static boolean showProgress = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.register();
        xin.vanilla.banira.api.client.event.BaniraClientEvents.Player.onClientLoggedIn(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.beginConnection());
        xin.vanilla.banira.api.client.event.BaniraClientEvents.Player.onClientLoggedOut(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.reset());
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEvents.Client.onClientTick(event -> onClientTick());
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_BAR, ClientGameEventHandler::interceptExperience);
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_TEXT, ClientGameEventHandler::interceptExperience);
    }

    private static void onClientTick() {
        Minecraft client = Minecraft.getInstance();
        AotakeUiSmokeRunner.tick(client);
        AotakeNetworkSmokeClientRunner.tick(client);
        if (client.screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                lastTime = System.currentTimeMillis();
                PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            }
            if (ClientConfig.get().progressBar().progressBarKeyApplyMode()) {
                if (ClientModEventHandler.PROGRESS_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                    lastTime = System.currentTimeMillis();
                    showProgress = !showProgress;
                }
            } else {
                showProgress = ClientModEventHandler.PROGRESS_KEY.isDown();
            }
        }
    }

    public static void renderHud(PoseStack stack, float partialTick) {
        ProgressRender.render(stack, showProgress);
    }

    static boolean hidesExperience(boolean held, java.util.List<xin.vanilla.aotake.enums.EnumProgressBarType> normal,
                                   boolean leaf, boolean pole, boolean text) {
        return held || normal.contains(xin.vanilla.aotake.enums.EnumProgressBarType.LEAF) && leaf
                || normal.contains(xin.vanilla.aotake.enums.EnumProgressBarType.POLE) && pole
                || normal.contains(xin.vanilla.aotake.enums.EnumProgressBarType.TEXT) && text;
    }

    private static void interceptExperience(BaniraHudRenderEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return;
        xin.vanilla.aotake.config.ClientConfigView.ProgressBarView progress = ClientConfig.get().progressBar();
        if (hidesExperience(showProgress, progress.progressBarDisplayNormal(),
                progress.leaf().hideExperienceBarLeaf(), progress.pole().hideExperienceBarPole(),
                progress.text().hideExperienceBarText())) {
            event.cancel();
        }
    }
}
