package xin.vanilla.aotake.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(
            method = "renderBg",
            at = @At("HEAD"),
            cancellable = true
    )
    private void aotake$interceptRenderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        ContainerScreen screen = (ContainerScreen) (Object) this;
        String title = screen.getTitle().getString();
        String modTitle = Component.translatable(EnumI18nType.KEY, "categories")
                .toTextComponent(AotakeUtils.getPlayerLanguage(player))
                .getString();
        if (!title.startsWith(modTitle)) return;
        if (ClientConfig.VANILLA_DUSTBIN.get()) return;

        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "dustbin_gui.png");
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
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
        AbstractGuiUtils.blitBlend(graphics, texture, drawX, drawY, 0, 0, drawW, drawH, srcW, srcH);

        ci.cancel();
    }
}
