package xin.vanilla.aotake.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xin.vanilla.aotake.event.ClientGameEventHandler;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(
            at = {@At("TAIL")},
            method = "hasExperience",
            cancellable = true
    )
    public void aotake$hasExperience(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && ClientGameEventHandler.shouldForceExperienceBarOverlay()) {
            cir.setReturnValue(true);
        }
    }
}
