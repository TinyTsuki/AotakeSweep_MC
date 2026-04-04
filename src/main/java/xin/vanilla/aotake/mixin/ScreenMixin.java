package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.screen.DustbinRender;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("HEAD"))
    private void aotake$abandonCursorRestoreIfNotDustbin(Minecraft mc, int width, int height, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (self instanceof ChestScreen && DustbinRender.isDustbinTitle(self.getTitle().getContents())) {
            return;
        }
        DustbinRender.abandonPendingCursorRestore();
    }

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void aotake$applyPendingCursorAfterInit(Minecraft mc, int width, int height, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof ChestScreen)) {
            return;
        }
        if (!DustbinRender.isDustbinTitle(self.getTitle().getContents())) {
            return;
        }
        DustbinRender.tryConsumePendingCursorRaw();
    }
}
