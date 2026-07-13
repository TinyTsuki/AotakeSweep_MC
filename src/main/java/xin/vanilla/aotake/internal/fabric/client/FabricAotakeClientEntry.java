package xin.vanilla.aotake.internal.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import xin.vanilla.aotake.client.AotakeClientBootstrap;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.screen.DustbinRender;

/** Fabric 客户端入口，避免服务端类加载触碰任何客户端类型。 */
public final class FabricAotakeClientEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AotakeClientBootstrap.init();
        HudRenderCallback.EVENT.register(ClientGameEventHandler::renderHud);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            DustbinRender.handleGuiScreen(new DustbinRender.InitPost(screen));
            ScreenEvents.afterRender(screen).register((scr, stack, mouseX, mouseY, tickDelta) ->
                    DustbinRender.handleGuiScreen(new DustbinRender.DrawPost(scr, stack, mouseX, mouseY)));
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyCode, scanCode, modifiers) -> {
                DustbinRender.KeyPressedPre event = new DustbinRender.KeyPressedPre(scr, keyCode, modifiers);
                DustbinRender.handleGuiScreen(event);
                return !event.isCanceled();
            });
        });
    }
}
