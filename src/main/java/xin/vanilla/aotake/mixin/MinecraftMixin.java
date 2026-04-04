package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.screen.DustbinRender;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(
            method = "setScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MouseHelper;releaseMouse()V",
                    shift = At.Shift.AFTER
            )
    )
    private void aotake$restoreDustbinCursorAfterReleaseMouseInSetScreen(Screen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        Screen s = mc.screen;
        if (!(s instanceof ChestScreen)) {
            return;
        }
        if (!DustbinRender.isDustbinTitle(s.getTitle().getContents())) {
            return;
        }
        DustbinRender.tryConsumePendingCursorRaw();
    }
}
