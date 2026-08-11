package xin.vanilla.aotake.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.event.ClientGameEventHandler;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void aotake$beforeExperienceBarRender(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        ClientGameEventHandler.onRenderOverlayPre(guiGraphics);
        if (ClientGameEventHandler.shouldCancelExperienceBarOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void aotake$afterExperienceBarRender(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        ClientGameEventHandler.onRenderOverlayPost(guiGraphics);
    }

    @Inject(method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), cancellable = true)
    private void aotake$beforeExperienceLevelRender(GuiGraphics guiGraphics, DeltaTracker tracker, CallbackInfo ci) {
        if (ClientGameEventHandler.shouldCancelExperienceBarOverlay()) {
            ci.cancel();
        }
    }
}
