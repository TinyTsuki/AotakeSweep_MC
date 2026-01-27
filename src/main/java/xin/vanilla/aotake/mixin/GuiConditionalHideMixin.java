package xin.vanilla.aotake.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xin.vanilla.aotake.event.ClientEventHandler;


@Mixin(Gui.class)
public abstract class GuiConditionalHideMixin {

    @Invoker("renderJumpMeter")
    public abstract void invokeRenderJumpMeter(PlayerRideableJumping playerRideableJumping, PoseStack stack, int x);

    @Invoker("renderExperienceBar")
    public abstract void invokeRenderExperienceBar(PoseStack stack, int x);

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lcom/mojang/blaze3d/vertex/PoseStack;I)V"
            )
    )
    private void aotake$redirectRenderJumpMeter(Gui guiInstance, PlayerRideableJumping playerRideableJumping, PoseStack stack, int x) {
        if (!ClientEventHandler.hideExpBar()) {
            ((GuiConditionalHideMixin) (Object) guiInstance).invokeRenderJumpMeter(playerRideableJumping, stack, x);
        }
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderExperienceBar(Lcom/mojang/blaze3d/vertex/PoseStack;I)V"
            )
    )
    private void aotake$redirectRenderExperienceBar(Gui guiInstance, PoseStack stack, int x) {
        if (!ClientEventHandler.hideExpBar()) {
            ((GuiConditionalHideMixin) (Object) guiInstance).invokeRenderExperienceBar(stack, x);
        }
    }
}
