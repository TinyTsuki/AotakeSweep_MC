package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
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
                    target = "Lnet/minecraft/client/MouseHandler;releaseMouse()V",
                    shift = At.Shift.AFTER
            )
    )
    private void aotake$restoreDustbinCursorAfterReleaseMouseInSetScreen(Screen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        Screen s = mc.screen;
        if (!(s instanceof ContainerScreen)) {
            return;
        }
        if (!DustbinRender.isDustbinTitle(s.getTitle().getContents())
                && !DustbinRender.isChunkVaultTitle(s.getTitle().getContents())) {
            return;
        }
        DustbinRender.tryConsumePendingCursorRaw();
    }
}
