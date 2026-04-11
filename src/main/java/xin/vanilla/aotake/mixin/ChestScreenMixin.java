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
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.DustbinGuiLayoutCache;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.screen.DustbinBaniraThemePaint;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.util.AbstractGuiUtils;
import xin.vanilla.banira.client.util.ClientThemeManager;
import xin.vanilla.banira.client.util.TextureUtils;

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
        EnumDustbinClientUiStyle ui = ClientConfig.get().dustbin().dustbinUiStyle();
        if (ui == EnumDustbinClientUiStyle.VANILLA) return;

        if (ui == EnumDustbinClientUiStyle.TEXTURED) {
            if (!DustbinGuiLayoutCache.valid) return;

            int leftPos = DustbinGuiLayoutCache.leftPos;
            int topPos = DustbinGuiLayoutCache.topPos;
            int drawWidth = DustbinGuiLayoutCache.drawWidth;
            int drawHeight = DustbinGuiLayoutCache.drawHeight;
            int srcWidth = DustbinGuiLayoutCache.srcWidth;
            int srcHeight = DustbinGuiLayoutCache.srcHeight;

            ResourceLocation texture = TextureUtils.loadCustomTexture(Identifier.id(), "gui/dustbin_gui.png");
            AbstractGuiUtils.renderByDepth(stack, 0, (s) ->
                    AbstractGuiUtils.blitBlend(s, texture, leftPos, topPos, 0, 0, drawWidth, drawHeight, srcWidth, srcHeight)
            );
            ci.cancel();
            return;
        }

        if (ui == EnumDustbinClientUiStyle.BANIRA_THEME) {
            ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
            int leftPos = acc.aotake$getLeftPos();
            int topPos = acc.aotake$getTopPos();
            int drawWidth = acc.aotake$getImageWidth();
            int drawHeight = acc.aotake$getImageHeight();
            BaniraColorConfig t = ClientThemeManager.getEffectiveTheme();
            int chestRows = (screen.getMenu().slots.size() - 36) / 9;
            if (chestRows < 1) {
                chestRows = 6;
            }
            final int chestRowsFinal = chestRows;
            AbstractGuiUtils.renderByDepth(stack, 0, (s) ->
                    DustbinBaniraThemePaint.renderFullThemeBackground(s, leftPos, topPos, drawWidth, drawHeight, t, chestRowsFinal)
            );
            ci.cancel();
        }
    }

    @Unique
    private boolean aotake$isDustbinScreen(ChestScreen screen) {
        PlayerEntity player = Minecraft.getInstance().player;
        if (player == null) return false;
        return DustbinRender.isDustbinTitle(screen.getTitle().getContents());
    }
}
