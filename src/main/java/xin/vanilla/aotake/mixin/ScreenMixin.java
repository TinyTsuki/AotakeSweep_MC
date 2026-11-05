package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.screen.DustbinRender;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "init(II)V", at = @At("HEAD"))
    private void aotake$abandonCursorRestoreIfNotDustbin(int width, int height, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof ContainerScreen && (DustbinRender.isDustbinTitle(self.getTitle().getString())
                || DustbinRender.isChunkVaultTitle(self.getTitle().getString()))) {
            return;
        }
        DustbinRender.abandonPendingCursorRestore();
    }

    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void aotake$applyPendingCursorAfterInit(int width, int height, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof ContainerScreen)) {
            return;
        }
        if (!DustbinRender.isDustbinTitle(self.getTitle().getString())
                && !DustbinRender.isChunkVaultTitle(self.getTitle().getString())) {
            return;
        }
        DustbinRender.tryConsumePendingCursorRaw();
    }
}
