package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.event.BaniraGuiOverlayEvent;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑；通过 {@link BaniraClientEventHub} 订阅，在 {@link xin.vanilla.aotake.AotakeSweep.ClientProxy} 中注册。
 */
public final class ClientGameEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final ResourceLocation EXPERIENCE_BAR = ResourceLocation.fromNamespaceAndPath("minecraft", "experience_bar");

    private static long lastTime = 0;
    private static boolean showProgress = false;
    private static boolean cancelExperienceBarOverlay = false;

    private ClientGameEventHandler() {
    }

    public static void register() {
        BaniraClientEventHub.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        BaniraClientEventHub.Client.onClientTick(ClientGameEventHandler::onClientTick);
        BaniraClientEventHub.Client.onGuiScreen(DustbinRender::handleGuiScreen);
        BaniraClientEventHub.Client.onRenderOverlayPre(ClientGameEventHandler::onRenderOverlayPre);
        BaniraClientEventHub.Client.onRenderOverlayPost(ClientGameEventHandler::onRenderOverlayPost);
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

    private static void onRenderOverlayPre(BaniraGuiOverlayEvent.Pre event) {
        if (EXPERIENCE_BAR.equals(event.overlayId())) {
            cancelExperienceBarOverlay = shouldForceExperienceBarOverlay()
                    || ProgressRender.shouldHideVanillaExperienceBar(showProgress);
            if (!cancelExperienceBarOverlay) {
                return;
            }
            ProgressRender.render(event, showProgress);
        }
    }

    private static void onRenderOverlayPost(BaniraGuiOverlayEvent.Post event) {
        if (EXPERIENCE_BAR.equals(event.overlayId())) {
            if (ProgressRender.shouldHideVanillaExperienceBar(showProgress)) {
                return;
            }
            ProgressRender.render(event, showProgress);
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
