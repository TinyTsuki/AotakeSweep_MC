package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.event.BaniraGuiOverlayEvent;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；NeoForge 21.1 的 HUD 拦截仍通过 Banira overlay 包装事件适配。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ResourceLocation EXPERIENCE_BAR = Identifier.id().create("minecraft", "experience_bar");

    private static long lastTime = 0;
    private static boolean showProgress = false;
    private static boolean cancelExperienceBarOverlay = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEventHub.Client.onClientTick(ClientGameEventHandler::onClientTick);
        BaniraClientEventHub.Client.onGuiScreen(DustbinRender::handleGuiScreen);
        BaniraClientEventHub.Client.onRenderOverlayPre(ClientGameEventHandler::onRenderOverlayPre);
        BaniraClientEventHub.Client.onRenderOverlayPost(ClientGameEventHandler::onRenderOverlayPost);
    }

    private static void onClientTick(ClientTickEvent event) {
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

    private static void onRenderOverlayPre(BaniraGuiOverlayEvent.Pre event) {
        if (EXPERIENCE_BAR.equals(event.overlayId())) {
            cancelExperienceBarOverlay = ProgressRender.shouldHideVanillaExperienceBar(showProgress);
        }
    }

    private static void onRenderOverlayPost(BaniraGuiOverlayEvent.Post event) {
    }

    public static boolean shouldCancelExperienceBarOverlay() {
        return cancelExperienceBarOverlay;
    }

    public static void renderProgressOverlay(GuiGraphics guiGraphics) {
        cancelExperienceBarOverlay = ProgressRender.shouldHideVanillaExperienceBar(showProgress);
        if (ProgressRender.shouldRenderProgressOverlay(showProgress)) {
            ProgressRender.render(guiGraphics, showProgress);
        }
    }
}
