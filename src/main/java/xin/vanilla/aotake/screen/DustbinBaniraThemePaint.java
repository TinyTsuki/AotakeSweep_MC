package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;

/**
 * Banira 主题下垃圾箱大箱界面的分区描边与槽位格线绘制
 */
public final class DustbinBaniraThemePaint {

    private static final int SLOT_STEP = 18;
    private static final int SLOT_ORIGIN_X = 8;
    private static final int CHEST_FIRST_Y = 18;
    private static final float PANEL_RADIUS = 3.0f;
    private static final float REGION_RADIUS = 2.5f;
    private static final float SLOT_RADIUS = 1.5f;
    private static final float SLOT_FILL_ALPHA = 0.28f;
    private static final float SLOT_BORDER_ALPHA = 0.20f;
    private static final int LIGHT_NEUTRAL = 0xFFF0F2EF;
    private static final int DARK_NEUTRAL = 0xFF252925;
    /**
     * 与原版物品格对齐：装饰性格子整体向左、向上各 1px
     */
    private static final int SLOT_GRID_NUDGE = 1;

    private DustbinBaniraThemePaint() {
    }

    public static int chestRowsPlayerYOffset(int chestRows) {
        return (chestRows - 4) * SLOT_STEP;
    }

    public static void renderFullThemeBackground(MatrixStack stack, int guiLeft, int guiTop, int imageW, int imageH,
                                                 BaniraColorConfig t, int chestRows) {
        int off = chestRowsPlayerYOffset(chestRows);
        int gl = guiLeft + SLOT_ORIGIN_X - SLOT_GRID_NUDGE;
        int chestTop = guiTop + CHEST_FIRST_Y - SLOT_GRID_NUDGE;
        int panelFill = neutralize(t.bgSurface(), 0.72f);
        int panelBorder = withAlpha(neutralize(t.border(), 0.68f), 0.62f);
        int regionFill = neutralize(t.bgQuaternary(), 0.64f);
        int regionBorder = withAlpha(neutralize(t.border(), 0.76f), 0.30f);

        drawFineRoundedRect(stack, guiLeft, guiTop, imageW, imageH, PANEL_RADIUS, panelFill, 0);
        drawFineRoundedRect(stack, guiLeft, guiTop, imageW, imageH, PANEL_RADIUS, panelBorder, 1);

        drawFineRoundedRect(stack, guiLeft + 1, guiTop + 1, imageW - 2, 16,
                REGION_RADIUS, neutralize(t.bgSecondary(), 0.54f), 0);
        drawFineRoundedRect(stack, guiLeft + 4, guiTop + 4, 2, 10, 1.0f, t.accent(), 0);

        int chestH = chestRows * SLOT_STEP;
        drawRegionPanel(stack, gl - 2, chestTop - 2, 9 * SLOT_STEP + 4, chestH + 4,
                regionFill, regionBorder);

        int invTop = guiTop + 103 + off - SLOT_GRID_NUDGE;
        drawRegionPanel(stack, gl - 2, invTop - 2, 9 * SLOT_STEP + 4, 3 * SLOT_STEP + 4,
                regionFill, regionBorder);

        int hotTop = guiTop + 161 + off - SLOT_GRID_NUDGE;
        drawRegionPanel(stack, gl - 2, hotTop - 2, 9 * SLOT_STEP + 4, SLOT_STEP + 4,
                regionFill, regionBorder);

        drawSlotCellGrid(stack, guiLeft, guiTop, chestRows, off, t);
    }

    private static void drawRegionPanel(MatrixStack stack, int x, int y, int w, int h,
                                        int fill, int border) {
        drawFineRoundedRect(stack, x, y, w, h, REGION_RADIUS, fill, 0);
        drawFineRoundedRect(stack, x, y, w, h, REGION_RADIUS, border, 1);
    }

    private static void drawSlotCellGrid(MatrixStack stack, int guiLeft, int guiTop, int chestRows, int playerOff, BaniraColorConfig t) {
        int slotLine = withAlpha(neutralize(t.border(), 0.82f), SLOT_BORDER_ALPHA);
        int slotFill = withAlpha(neutralize(t.bgQuaternary(), 0.78f), SLOT_FILL_ALPHA);
        int gl = guiLeft + SLOT_ORIGIN_X - SLOT_GRID_NUDGE;
        int gt = guiTop + CHEST_FIRST_Y - SLOT_GRID_NUDGE;
        for (int r = 0; r < chestRows; r++) {
            for (int c = 0; c < 9; c++) {
                drawOneSlotCell(stack, gl + c * SLOT_STEP, gt + r * SLOT_STEP, slotFill, slotLine);
            }
        }
        int invTop = guiTop + 103 + playerOff - SLOT_GRID_NUDGE;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawOneSlotCell(stack, gl + c * SLOT_STEP, invTop + r * SLOT_STEP, slotFill, slotLine);
            }
        }
        int hotTop = guiTop + 161 + playerOff - SLOT_GRID_NUDGE;
        for (int c = 0; c < 9; c++) {
            drawOneSlotCell(stack, gl + c * SLOT_STEP, hotTop, slotFill, slotLine);
        }
    }

    private static void drawOneSlotCell(MatrixStack stack, int x, int y, int innerFill, int line) {
        drawFineRoundedRect(stack, x + 1, y + 1, SLOT_STEP - 2, SLOT_STEP - 2,
                SLOT_RADIUS, innerFill, 0);
        drawFineRoundedRect(stack, x, y, SLOT_STEP, SLOT_STEP, SLOT_RADIUS + 0.5f, line, 1);
    }

    private static void drawFineRoundedRect(MatrixStack stack, int x, int y, int w, int h,
                                            float radius, int color, int border) {
        ShapeDrawArgs shape = ShapeDrawArgs.rect(stack, x, y, w, h, color);
        shape.rect().radius(radius).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(border);
        BaseShapeWidget.drawShape(shape);
    }

    private static int neutralize(int color, float amount) {
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int neutral = (r * 30 + g * 59 + b * 11) / 100 >= 128 ? LIGHT_NEUTRAL : DARK_NEUTRAL;
        return mixArgb(color, neutral, amount);
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
