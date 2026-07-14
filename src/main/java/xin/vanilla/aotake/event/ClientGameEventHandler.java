package xin.vanilla.aotake.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.hud.BaniraHudEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudRenderEvent;
import xin.vanilla.banira.api.client.hud.HudOverlayElement;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；通过 Banira 客户端事件门面订阅。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static long lastTime = 0;
    private static boolean showProgress = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEvents.Client.onClientTick(event -> onClientTick());
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_BAR, ClientGameEventHandler::interceptExperience);
        BaniraHudEvents.onElementPreRender(HudOverlayElement.EXPERIENCE_TEXT, ClientGameEventHandler::interceptExperience);
    }

    private static void onClientTick() {
        AotakeUiSmokeRunner.tick(Minecraft.getInstance());
        if (Minecraft.getInstance().screen == null) {
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

    public static void renderHud(GuiGraphics graphics, float partialTick) {
        ProgressRender.render(graphics.pose(), showProgress);
    }

    /** 按住进度键时由 Aotake 接管原版经验区，普通状态仍保留原版绘制。 */
    private static void interceptExperience(BaniraHudRenderEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (showProgress && minecraft.player != null && minecraft.screen == null && !minecraft.options.hideGui) {
            event.cancel();
        }
    }
}
