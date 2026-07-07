package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；Forge HUD/Screen 原生事件仍用于需要拦截底层渲染的场景。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static long lastTime = 0;
    private static boolean showProgress = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> ClientGameEventHandler.onClientTick(event));
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent event) -> DustbinRender.handleGuiScreen(event));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Pre event) -> ClientGameEventHandler.onRenderOverlayPre(event));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Post event) -> ClientGameEventHandler.onRenderOverlayPost(event));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
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

    private static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            ProgressRender.render(event, showProgress);
        }
    }

    private static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            ProgressRender.render(event, showProgress);
        }
    }
}
