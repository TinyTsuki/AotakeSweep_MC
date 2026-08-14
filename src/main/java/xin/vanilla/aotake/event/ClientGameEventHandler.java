package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；NeoForge 21.1 的经验条拦截由 Aotake Mixin 转发到此处。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static long lastTime = 0;
    private static boolean showProgress = false;
    private static boolean cancelExperienceBarOverlay = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientGameEventHandler.onClientTick(event));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> DustbinRender.handleGuiScreen(event));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post event) -> DustbinRender.handleGuiScreen(event));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> DustbinRender.handleGuiScreen(event));
    }

    private static void onClientTick(ClientTickEvent.Post event) {
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

    public static void onRenderOverlayPre() {
        cancelExperienceBarOverlay = ProgressRender.shouldHideVanillaExperienceBar(showProgress);
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
