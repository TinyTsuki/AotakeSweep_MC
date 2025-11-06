package xin.vanilla.aotake.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xin.vanilla.aotake.screen.ProgressRender;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(
            at = {@At("TAIL")},
            method = "hasExperience",
            cancellable = true
    )
    public void hasExperience(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ProgressRender.experienceSupplier.getAsBoolean());
    }
}
