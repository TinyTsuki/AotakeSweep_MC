package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.Redirect;
import xin.vanilla.aotake.event.ClientEventHandler;


@Mixin(Gui.class)
public abstract class GuiConditionalHideMixin {

    @Invoker("renderJumpMeter")
    public abstract void invokeRenderJumpMeter(PlayerRideableJumping playerRideableJumping, GuiGraphics guiGraphics, int x);

    @Invoker("renderExperienceBar")
    public abstract void invokeRenderExperienceBar(GuiGraphics guiGraphics, int x);

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;Lnet/minecraft/client/gui/GuiGraphics;I)V"
            )
    )
    private void aotake$redirectRenderJumpMeter(Gui guiInstance, PlayerRideableJumping playerRideableJumping, GuiGraphics guiGraphics, int x) {
        if (!ClientEventHandler.hideExpBar()) {
            ((GuiConditionalHideMixin) (Object) guiInstance).invokeRenderJumpMeter(playerRideableJumping, guiGraphics, x);
        }
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V"
            )
    )
    private void aotake$redirectRenderExperienceBar(Gui guiInstance, GuiGraphics guiGraphics, int x) {
        if (!ClientEventHandler.hideExpBar()) {
            ((GuiConditionalHideMixin) (Object) guiInstance).invokeRenderExperienceBar(guiGraphics, x);
        }
    }
}
