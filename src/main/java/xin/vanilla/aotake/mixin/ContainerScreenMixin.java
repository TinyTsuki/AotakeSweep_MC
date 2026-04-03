package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.client.util.TextureUtils;
import xin.vanilla.banira.common.data.KeyValue;

@Mixin(ContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void aotake$adjustDustbinLayout(CallbackInfo ci) {
        ContainerScreen screen = (ContainerScreen) (Object) this;
        if (!(screen instanceof ChestScreen)) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }
        if (!aotake$isDustbinScreen((ChestScreen) screen)) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }
        EnumDustbinClientUiStyle ui = ClientConfig.get().dustbin().dustbinUiStyle();
        if (ui == EnumDustbinClientUiStyle.VANILLA || ui == EnumDustbinClientUiStyle.BANIRA_THEME) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }

        DustbinGuiConfig.reload();
        ResourceLocation texture = TextureUtils.loadCustomTexture(Identifier.id(), "gui/dustbin_gui.png");
        KeyValue<Integer, Integer> size = TextureUtils.getTextureSize(texture);
        int srcW = size.key();
        int srcH = size.val();
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
    private boolean aotake$isDustbinScreen(ChestScreen screen) {
        PlayerEntity player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return false;
        String t = screen.getTitle().getContents();
        return DustbinRender.isDustbinTitle(t) || DustbinRender.isChunkVaultTitle(t);
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
