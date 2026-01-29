package xin.vanilla.aotake.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AbstractGuiUtils;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.TextureUtils;

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
        String title = screen.getTitle().getContents();
        String modTitle = Component.translatable(EnumI18nType.KEY, "categories")
                .toTextComponent(AotakeUtils.getPlayerLanguage(player))
                .getContents();
        if (!title.startsWith(modTitle)) return;
        if (ClientConfig.VANILLA_DUSTBIN.get()) return;

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "dustbin_gui.png");
        AbstractGuiUtils.bindTexture(texture);
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int i = accessor.aotake$getLeftPos();
        int j = accessor.aotake$getTopPos();
        int imageWidth = accessor.aotake$getImageWidth();
        int imageHeight = accessor.aotake$getImageHeight();
        KeyValue<Integer, Integer> size = TextureUtils.getTextureSize(texture);
        int srcW = size.getKey();
        int srcH = size.getValue();
        double scale = Math.min(imageWidth / (double) srcW, imageHeight / (double) srcH);
        int drawW = (int) Math.round(srcW * scale);
        int drawH = (int) Math.round(srcH * scale);
        int drawX = i + (imageWidth - drawW) / 2;
        int drawY = j + (imageHeight - drawH) / 2;
        AbstractGuiUtils.blitBlend(stack, drawX, drawY, 0, 0, drawW, drawH, srcW, srcH);

        ci.cancel();
    }
}
