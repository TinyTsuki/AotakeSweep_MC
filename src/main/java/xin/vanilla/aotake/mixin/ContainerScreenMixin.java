package xin.vanilla.aotake.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AbstractGuiUtils;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.TextureUtils;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(
            method = "renderBg",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aotake$interceptRenderBg(PoseStack stack, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ContainerScreen screen = (ContainerScreen) (Object) this;
        if (!aotake$isDustbinScreen(screen)) return;
        if (ClientConfig.VANILLA_DUSTBIN.get()) return;
        if (!DustbinGuiLayoutCache.valid) return;

        int leftPos = DustbinGuiLayoutCache.leftPos;
        int topPos = DustbinGuiLayoutCache.topPos;
        int drawWidth = DustbinGuiLayoutCache.drawWidth;
        int drawHeight = DustbinGuiLayoutCache.drawHeight;
        int srcWidth = DustbinGuiLayoutCache.srcWidth;
        int srcHeight = DustbinGuiLayoutCache.srcHeight;

        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "dustbin_gui.png");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        AbstractGuiUtils.bindTexture(texture);
        AbstractGuiUtils.renderByDepth(stack, 0, (s) -> AbstractGuiUtils.blitByBlend(() ->
                GuiComponent.blit(s, leftPos, topPos, 0, 0, drawWidth, drawHeight, srcWidth, srcHeight)
        ));
        ci.cancel();
    }

    @Unique
    private boolean aotake$isDustbinScreen(ContainerScreen screen) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        String title = screen.getTitle().getContents();
        String modTitle = Component.translatable(EnumI18nType.WORD, "title")
                .toTextComponent(AotakeUtils.getPlayerLanguage(player))
                .getContents();
        return title.startsWith(modTitle);
    }
}
