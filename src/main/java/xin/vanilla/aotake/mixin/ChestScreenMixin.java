package xin.vanilla.aotake.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.util.Translator;

@Mixin(ChestScreen.class)
public abstract class ChestScreenMixin {

    @Inject(
            method = "renderBg",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aotake$interceptRenderBg(MatrixStack stack, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;
        ChestScreen screen = (ChestScreen) (Object) this;
        if (!aotake$isDustbinScreen(screen)) return;
        if (ClientConfig.get().dustbin().vanillaDustbin()) return;
        if (!DustbinGuiLayoutCache.valid) return;

        int leftPos = DustbinGuiLayoutCache.leftPos;
        int topPos = DustbinGuiLayoutCache.topPos;
        int drawWidth = DustbinGuiLayoutCache.drawWidth;
        int drawHeight = DustbinGuiLayoutCache.drawHeight;
        int srcWidth = DustbinGuiLayoutCache.srcWidth;
        int srcHeight = DustbinGuiLayoutCache.srcHeight;

        ResourceLocation texture = TextureUtils.loadCustomTexture(Identifier.id(), "textures/gui/dustbin_gui.png");
        AbstractGuiUtils.renderByDepth(stack, 0, (s) ->
                AbstractGuiUtils.blitBlend(s, texture, leftPos, topPos, 0, 0, drawWidth, drawHeight, srcWidth, srcHeight)
        );
        ci.cancel();
    }

    @Unique
    private boolean aotake$isDustbinScreen(ChestScreen screen) {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) return false;
        String title = screen.getTitle().getContents();
        String modTitle = AotakeComponent.get().transAuto("title")
                .toVanilla(Translator.getClientLanguage())
                .getContents();
        return title.startsWith(modTitle);
    }
}
