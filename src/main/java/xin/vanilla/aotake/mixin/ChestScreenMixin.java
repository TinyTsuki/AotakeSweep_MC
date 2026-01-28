package xin.vanilla.aotake.mixin;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        AbstractGuiUtils.bindTexture(TextureUtils.loadCustomTexture(TextureUtils.INTERNAL_THEME_DIR + "dustbin_gui.png"));
        ContainerScreenAccessor accessor = (ContainerScreenAccessor) screen;
        int i = accessor.aotake$getLeftPos();
        int j = accessor.aotake$getTopPos();
        int imageWidth = accessor.aotake$getImageWidth();
        int rowsHeight = 6 * 18 + 17;
        AbstractGuiUtils.blitBlend(stack, i, j, 0, 0, imageWidth, rowsHeight, 256, 256);
        AbstractGuiUtils.blitBlend(stack, i, j + rowsHeight, 0, 126, imageWidth, 96, 256, 256);

        ci.cancel();
    }
}
