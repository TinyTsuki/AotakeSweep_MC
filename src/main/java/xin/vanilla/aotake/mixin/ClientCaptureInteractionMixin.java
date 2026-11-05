package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xin.vanilla.aotake.event.EventHandlerProxy;

@Mixin(MultiPlayerGameMode.class)
abstract class ClientCaptureInteractionMixin {
    @Inject(method = "interact", at = @At("RETURN"), cancellable = true)
    private void aotake$consumeLocalCapture(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> callback) {
        // The entity packet was already sent. Only predict where the server shares our configuration.
        if (callback.getReturnValue() == InteractionResult.PASS && Minecraft.getInstance().hasSingleplayerServer()
                && !player.isSpectator() && target.isAlive() && !(target instanceof Player)
                && EventHandlerProxy.canAttemptCapture(player, player.getItemInHand(hand))) {
            callback.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
