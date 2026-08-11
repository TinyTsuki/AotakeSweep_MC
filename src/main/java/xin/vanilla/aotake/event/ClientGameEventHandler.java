package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
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
 * 客户端 Game 逻辑；21.1 的经验条拦截由 Aotake Mixin 转发到此处。
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
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> ClientGameEventHandler.onClientTick(event));
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent event) -> DustbinRender.handleGuiScreen(event));
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!(event instanceof TickEvent.ClientTickEvent.Post)) {
            return;
        }
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

    public static void onRenderOverlayPre(GuiGraphics guiGraphics) {
        cancelExperienceBarOverlay = shouldForceExperienceBarOverlay()
                || ProgressRender.shouldHideVanillaExperienceBar(showProgress);
        if (cancelExperienceBarOverlay) {
            ProgressRender.render(guiGraphics, showProgress);
        }
    }

    public static void onRenderOverlayPost(GuiGraphics guiGraphics) {
        if (!ProgressRender.shouldHideVanillaExperienceBar(showProgress)) {
            ProgressRender.render(guiGraphics, showProgress);
        }
    }

    public static boolean shouldCancelExperienceBarOverlay() {
        return cancelExperienceBarOverlay;
    }

    public static boolean shouldForceExperienceBarOverlay() {
        Minecraft mc = Minecraft.getInstance();
        return ProgressRender.shouldRenderProgressOverlay(showProgress)
                && mc.gameMode != null
                && mc.gameMode.getPlayerMode() != GameType.SURVIVAL
                && mc.gameMode.getPlayerMode() != GameType.ADVENTURE;
    }
}
