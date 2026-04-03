package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.event.BaniraGuiOverlayEvent;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void aotake$beforeExperienceBarRender(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        BaniraClientEventHub.Client.fireRenderOverlayPre(
                new BaniraGuiOverlayEvent.Pre(guiGraphics, mc.getPartialTick(), ClientGameEventHandler.EXPERIENCE_BAR)
        );
        if (ClientGameEventHandler.shouldCancelExperienceBarOverlay()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void aotake$afterExperienceBarRender(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        BaniraClientEventHub.Client.fireRenderOverlayPost(
                new BaniraGuiOverlayEvent.Post(guiGraphics, mc.getPartialTick(), ClientGameEventHandler.EXPERIENCE_BAR)
        );
    }

    @Inject(method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;F)V", at = @At("HEAD"), cancellable = true)
    private void aotake$beforeExperienceLevelRender(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        ClientGameEventHandler.renderProgressOverlay(guiGraphics);
        if (ClientGameEventHandler.shouldCancelExperienceBarOverlay()) {
            ci.cancel();
        }
    }
}
