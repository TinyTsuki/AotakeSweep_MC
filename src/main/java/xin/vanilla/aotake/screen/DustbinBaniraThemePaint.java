package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;

/**
 * 使用 Banira 当前主题绘制垃圾箱的单层收纳柜背景
 */
public final class DustbinBaniraThemePaint {

    private static final int SLOT_STEP = 18;
    private static final int SLOT_ORIGIN_X = 8;
    private static final int CHEST_FIRST_Y = 18;
    private static final int PLAYER_SURFACE_BASE_Y = 90;
    private static final float CABINET_RADIUS = 4.0f;
    private static final float HEADER_RADIUS = 3.0f;
    private static final float CONTENT_RADIUS = 2.5f;
    private static final float SLOT_RADIUS = 1.5f;
    private static final float SLOT_FILL_ALPHA = 0.30f;
    private static final float SLOT_BORDER_ALPHA = 0.42f;
    private static final int SLOT_GRID_NUDGE = 1;

    private DustbinBaniraThemePaint() {
    }

    public static int chestRowsPlayerYOffset(int chestRows) {
        return (chestRows - 4) * SLOT_STEP;
    }

    /**
     * 绘制柜体、两个内容层级和与原版槽位对齐的轻量底面
     */
    public static void renderFullThemeBackground(PoseStack stack, int guiLeft, int guiTop, int imageW, int imageH,
                                                 BaniraColorConfig t, int chestRows) {
        int playerOffset = chestRowsPlayerYOffset(chestRows);
        int cabinetFill = t.bgSurface();
        int cabinetBorder = withAlpha(t.border(), 0.86f);

        drawFineRoundedRect(stack, guiLeft, guiTop, imageW, imageH,
                CABINET_RADIUS, cabinetFill, 0);
        drawFineRoundedRect(stack, guiLeft, guiTop, imageW, imageH,
                CABINET_RADIUS, cabinetBorder, 1);

        drawHeaderSurface(stack, guiLeft, guiTop, imageW, t);
        drawDustbinSurface(stack, guiLeft, guiTop, imageW, chestRows, t);
        drawPlayerInventorySurface(stack, guiLeft, guiTop, imageW, imageH, playerOffset, t);
        drawSlotSurfaceGrid(stack, guiLeft, guiTop, chestRows, playerOffset, t);
    }

    private static void drawHeaderSurface(PoseStack stack, int guiLeft, int guiTop, int imageW,
                                          BaniraColorConfig t) {
        drawFineRoundedRect(stack, guiLeft + 1, guiTop + 1, imageW - 2, 15,
                HEADER_RADIUS, t.bgSecondary(), 0);
        drawFineRoundedRect(stack, guiLeft + 5, guiTop + 4, 2, 9,
                1.0f, t.accent(), 0);
    }

    private static void drawDustbinSurface(PoseStack stack, int guiLeft, int guiTop, int imageW,
                                           int chestRows, BaniraColorConfig t) {
        int contentHeight = chestRows * SLOT_STEP + 2;
        int fill = mixArgb(t.bgQuaternary(), t.bgSurface(), 0.34f);
        drawFineRoundedRect(stack, guiLeft + 5, guiTop + 16, imageW - 10, contentHeight,
                CONTENT_RADIUS, fill, 0);
    }

    private static void drawPlayerInventorySurface(PoseStack stack, int guiLeft, int guiTop,
                                                   int imageW, int imageH, int playerOffset,
                                                   BaniraColorConfig t) {
        int surfaceY = guiTop + PLAYER_SURFACE_BASE_Y + playerOffset;
        int surfaceHeight = Math.max(1, imageH - PLAYER_SURFACE_BASE_Y - playerOffset - 2);
        int fill = mixArgb(t.bgSecondary(), t.bgSurface(), 0.48f);
        drawFineRoundedRect(stack, guiLeft + 5, surfaceY, imageW - 10, surfaceHeight,
                CONTENT_RADIUS, fill, 0);

        int hotbarTop = guiTop + 161 + playerOffset - SLOT_GRID_NUDGE;
        int separatorY = hotbarTop - 4;
        drawFineRoundedRect(stack, guiLeft + 8, separatorY, imageW - 16, 1,
                0.0f, withAlpha(t.border(), 0.52f), 0);
    }

    private static void drawSlotSurfaceGrid(PoseStack stack, int guiLeft, int guiTop,
                                            int chestRows, int playerOffset, BaniraColorConfig t) {
        int slotFill = withAlpha(mixArgb(t.bgQuaternary(), t.bgSurface(), 0.20f), SLOT_FILL_ALPHA);
        int slotBorder = withAlpha(t.border(), SLOT_BORDER_ALPHA);
        int slotLeft = guiLeft + SLOT_ORIGIN_X - SLOT_GRID_NUDGE;
        int chestTop = guiTop + CHEST_FIRST_Y - SLOT_GRID_NUDGE;
        for (int row = 0; row < chestRows; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotSurface(stack, slotLeft + column * SLOT_STEP,
                        chestTop + row * SLOT_STEP, slotFill, slotBorder);
            }
        }

        int inventoryTop = guiTop + 103 + playerOffset - SLOT_GRID_NUDGE;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotSurface(stack, slotLeft + column * SLOT_STEP,
                        inventoryTop + row * SLOT_STEP, slotFill, slotBorder);
            }
        }

        int hotbarTop = guiTop + 161 + playerOffset - SLOT_GRID_NUDGE;
        for (int column = 0; column < 9; column++) {
            drawSlotSurface(stack, slotLeft + column * SLOT_STEP, hotbarTop, slotFill, slotBorder);
        }
    }

    private static void drawSlotSurface(PoseStack stack, int x, int y, int fill, int border) {
        drawFineRoundedRect(stack, x + 1, y + 1, SLOT_STEP - 2, SLOT_STEP - 2,
                SLOT_RADIUS, fill, 0);
        drawFineRoundedRect(stack, x, y, SLOT_STEP, SLOT_STEP,
                SLOT_RADIUS + 0.5f, border, 1);
    }

    private static void drawFineRoundedRect(PoseStack stack, int x, int y, int w, int h,
                                            float radius, int color, int border) {
        ShapeDrawArgs shape = ShapeDrawArgs.rect(stack, x, y, w, h, color);
        shape.rect().radius(radius).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border);
        BaseShapeWidget.drawShape(shape);
    }

    private static int mixArgb(int from, int to, float amount) {
        float clamped = Math.max(0.0f, Math.min(1.0f, amount));
        int a = mixChannel((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, clamped);
        int r = mixChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, clamped);
        int g = mixChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, clamped);
        int b = mixChannel(from & 0xFF, to & 0xFF, clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mixChannel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    private static int withAlpha(int color, float alpha) {
        int sourceAlpha = (color >>> 24) & 0xFF;
        int appliedAlpha = Math.round(sourceAlpha * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (appliedAlpha << 24) | (color & 0xFFFFFF);
    }
}
