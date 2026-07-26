package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 调用原版控件注册入口，使控件同时参与绘制和输入。
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addButton")
    Widget aotake$addButton(Widget button);
}
