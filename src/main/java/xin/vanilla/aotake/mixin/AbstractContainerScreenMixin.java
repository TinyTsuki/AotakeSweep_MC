package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.enums.EnumI18nType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.Component;
import xin.vanilla.aotake.util.TextureUtils;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void aotake$adjustDustbinLayout(CallbackInfo ci) {
        AbstractContainerScreen screen = (AbstractContainerScreen) (Object) this;
        if (!(screen instanceof ContainerScreen)) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }
        if (!aotake$isDustbinScreen((ContainerScreen) screen)) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }
        if (ClientConfig.VANILLA_DUSTBIN.get()) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }

        DustbinGuiConfig.reload();
        ResourceLocation texture = TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "dustbin_gui.png");
        KeyValue<Integer, Integer> size = TextureUtils.getTextureSize(texture);
        int srcW = size.getKey();
        int srcH = size.getValue();
        if (srcW <= 0 || srcH <= 0) return;

        int screenWidth = screen.width;
        int screenHeight = screen.height;
        int[] scaled = aotake$computeScaledSize(screenWidth, screenHeight, srcW, srcH);
        int drawW = scaled[0];
        int drawH = scaled[1];
        int offsetX = DustbinGuiConfig.getXOffset();
        int offsetY = DustbinGuiConfig.getYOffset();

        int leftPos = (screenWidth - drawW) / 2 + offsetX;
        int topPos = (screenHeight - drawH) / 2 + offsetY;

        DustbinGuiLayoutCache.set(leftPos, topPos, drawW, drawH, srcW, srcH);
    }

    @Unique
    private boolean aotake$isDustbinScreen(ContainerScreen screen) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return false;
        String title = screen.getTitle().getString();
        String modTitle = Component.translatable(EnumI18nType.WORD, "title")
                .toTextComponent(AotakeUtils.getPlayerLanguage(player))
                .getString();
        return title.startsWith(modTitle);
    }

    @Unique
    private int[] aotake$computeScaledSize(int destW, int destH, int srcW, int srcH) {
        double scale;
        switch (DustbinGuiConfig.getScaleMode()) {
            case WIDTH:
                scale = destW / (double) srcW;
                break;
            case HEIGHT:
                scale = destH / (double) srcH;
                break;
            case NONE:
                scale = 1.0;
                break;
            case FIT:
            default:
                scale = Math.min(destW / (double) srcW, destH / (double) srcH);
                break;
        }
        return new int[]{(int) Math.round(srcW * scale), (int) Math.round(srcH * scale)};
    }
}
