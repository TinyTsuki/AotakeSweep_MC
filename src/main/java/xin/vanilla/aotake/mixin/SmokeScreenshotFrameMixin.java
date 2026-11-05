package xin.vanilla.aotake.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.internal.client.dev.SmokeScreenshotCapture;

@Mixin(GameRenderer.class)
public abstract class SmokeScreenshotFrameMixin {
    @Inject(method = "render", at = @At("RETURN"))
    private void aotake$afterFrame(CallbackInfo callback) {
        SmokeScreenshotCapture.afterFrame();
    }
}
