package xin.vanilla.aotake.internal.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import xin.vanilla.aotake.client.AotakeClientBootstrap;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.client.gui.widget.TooltipWidget;

/**
 * Fabric 客户端入口，避免服务端类加载触碰任何客户端类型。
 */
public final class FabricAotakeClientEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AotakeClientBootstrap.init();
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("aotake_sweep", "cleanup"),
                ClientGameEventHandler::renderHud);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            DustbinRender.handleGuiScreen(new DustbinRender.InitPost(screen,
                    button -> Screens.getWidgets(screen).add(button)));
            ScreenMouseEvents.allowMouseClick(screen).register((scr, event) ->
                    !xin.vanilla.aotake.client.ui.AotakeUiPages.click(scr, event.x(), event.y(), event.button()));
            ScreenMouseEvents.allowMouseRelease(screen).register((scr, event) ->
                    !xin.vanilla.aotake.client.ui.AotakeUiPages.release(scr, event.button()));
            ScreenEvents.afterExtract(screen).register((scr, stack, mouseX, mouseY, tickDelta) -> {
                boolean ownsTooltipFrame = TooltipWidget.beginPopupFrameIfIdle(scr, mouseX, mouseY);
                try (var extraction = xin.vanilla.banira.client.util.ExtractedGuiRenderer.bind(stack)) {
                    DustbinRender.handleGuiScreen(new DustbinRender.DrawPost(scr, stack, mouseX, mouseY));
                } finally {
                    if (ownsTooltipFrame) {
                        TooltipWidget.flushSubmittedPopupFrame(stack);
                    }
                }
            });
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key) -> {
                DustbinRender.KeyPressedPre event = new DustbinRender.KeyPressedPre(scr, key.key(), key.modifiers());
                DustbinRender.handleGuiScreen(event);
                return !event.isCanceled();
            });
        });
    }
}
