package xin.vanilla.aotake.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.client.ui.AotakeUiPages;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.aotake.screen.ProgressRender;
import xin.vanilla.banira.api.client.event.BaniraClientEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudEvents;
import xin.vanilla.banira.api.client.hud.BaniraHudRenderEvent;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;
import xin.vanilla.banira.client.util.ExtractedGuiRenderer;
import xin.vanilla.banira.common.util.PacketUtils;

/**
 * 客户端 Game 逻辑。
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
        BaniraClientEvents.Player.onClientLoggedIn(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.beginConnection());
        BaniraClientEvents.Player.onClientLoggedOut(
                player -> xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown.reset());
        BaniraClientEvents.Player.onClientLoggedOut(player -> {
            PROGRESS_STATE.reset();
            showProgress = false;
            ProgressRender.reset();
        });
        BaniraClientEvents.Player.onClientLoggedOut(player -> LOGGER.debug("Client: Player logged out."));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientGameEventHandler.onClientTick(event));
        BaniraHudEvents.onExperiencePreRender(ClientGameEventHandler::interceptExperience);
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> renderProgressOverlay(event.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> DustbinRender.handleGuiScreen(
                new DustbinRender.InitPost(event.getScreen(), event::addListener)));
        NeoForge.EVENT_BUS.addListener(ClientGameEventHandler::renderDustbin);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
            var input = new DustbinRender.KeyPressedPre(event.getScreen(), event.getKeyCode(), event.getModifiers());
            DustbinRender.handleGuiScreen(input);
            if (input.isCanceled()) event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre event) -> {
            if (AotakeUiPages.click(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton()))
                event.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre event) -> {
            if (AotakeUiPages.release(event.getScreen(), event.getButton())) event.setCanceled(true);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        AotakeUiSmokeRunner.tick(Minecraft.getInstance());
        xin.vanilla.aotake.internal.client.dev.AotakeNetworkSmokeClientRunner.tick(Minecraft.getInstance());
        showProgress = PROGRESS_STATE.tick(ClientModEventHandler.PROGRESS_KEY.isDown(), Minecraft.getInstance().screen != null,
                ClientConfig.get().progressBar().progressBarKeyApplyMode());
        if (Minecraft.getInstance().screen == null) {
            if (ClientModEventHandler.DUSTBIN_KEY.isDown() && System.currentTimeMillis() - lastTime > 100) {
                lastTime = System.currentTimeMillis();
                PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            }
        }
    }

    private static void interceptExperience(BaniraHudRenderEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || client.options.hideGui) return;
        if (ProgressRender.shouldRenderProgressOverlay(showProgress)
                && ProgressRender.shouldHideVanillaExperienceBar(showProgress)) event.cancel();
    }

    private static void renderDustbin(ScreenEvent.Render.Post event) {
        var screen = event.getScreen();
        var graphics = event.getGuiGraphics();
        int x = event.getMouseX(), y = event.getMouseY();
        boolean ownsTooltipFrame = TooltipWidget.beginPopupFrameIfIdle(screen, x, y);
        try (var extraction = ExtractedGuiRenderer.bind(graphics)) {
            DustbinRender.handleGuiScreen(new DustbinRender.DrawPost(screen, graphics, x, y));
        } finally {
            if (ownsTooltipFrame) TooltipWidget.flushSubmittedPopupFrame(graphics);
        }
    }

    public static void renderProgressOverlay(GuiGraphicsExtractor guiGraphics) {
        if (ProgressRender.shouldRenderProgressOverlay(showProgress)) {
            ProgressRender.render(guiGraphics, showProgress);
        } else ProgressRender.reset();
    }
}
