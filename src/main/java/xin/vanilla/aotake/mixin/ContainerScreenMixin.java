package xin.vanilla.aotake.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.player.Player;
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

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void aotake$renderUiLabels(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (xin.vanilla.aotake.client.ui.AotakeUiPages.owns((AbstractContainerScreen) (Object) this)) ci.cancel();
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void aotake$releaseUiAppearance(CallbackInfo ci) {
        xin.vanilla.aotake.client.ui.AotakeUiPages.close((AbstractContainerScreen) (Object) this);
    }

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
        EnumDustbinClientUiStyle ui = ClientConfig.get().dustbin().dustbinUiStyle();
        if (ui == EnumDustbinClientUiStyle.VANILLA || ui == EnumDustbinClientUiStyle.BANIRA_THEME) {
            DustbinGuiLayoutCache.invalidate();
            return;
        }

        DustbinGuiConfig.reload();
        net.minecraft.resources.Identifier texture = TextureUtils.loadCustomTexture(Identifier.id(), "gui/dustbin_gui.png");
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
    private boolean aotake$isDustbinScreen(ContainerScreen screen) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return false;
        String t = screen.getTitle().getString();
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
