package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.util.AbstractGuiUtils;

/**
 * Banira 主题下垃圾箱大箱界面的分区描边与槽位格线绘制
 */
public final class DustbinBaniraThemePaint {

    private static final int SLOT_STEP = 18;
    private static final int SLOT_ORIGIN_X = 8;
    private static final int CHEST_FIRST_Y = 18;
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

        AbstractGuiUtils.fillOutLine(stack, guiLeft, guiTop, imageW, imageH, 1, t.border());
        AbstractGuiUtils.fill(stack, guiLeft + 1, guiTop + 1, imageW - 2, imageH - 2, t.bgSurface());

        AbstractGuiUtils.fill(stack, guiLeft + 1, guiTop + 1, imageW - 2, 16, t.bgSecondary());
        AbstractGuiUtils.fill(stack, guiLeft + 4, guiTop + 4, 2, 10, t.accent());

        int chestH = chestRows * SLOT_STEP;
        drawRegionBoundary(stack, gl - 2, chestTop - 2, 9 * SLOT_STEP + 4, chestH + 4, t.accent(), t);

        int invTop = guiTop + 103 + off - SLOT_GRID_NUDGE;
        drawRegionBoundary(stack, gl - 2, invTop - 2, 9 * SLOT_STEP + 4, 3 * SLOT_STEP + 4, t.borderHover(), t);

        int hotTop = guiTop + 161 + off - SLOT_GRID_NUDGE;
        drawRegionBoundary(stack, gl - 2, hotTop - 2, 9 * SLOT_STEP + 4, SLOT_STEP + 4, t.accentFocused(), t);

        drawSlotCellGrid(stack, guiLeft, guiTop, chestRows, off, t);
    }

    private static void drawRegionBoundary(MatrixStack stack, int x, int y, int w, int h, int accentRgbLike, BaniraColorConfig t) {
        AbstractGuiUtils.fillOutLine(stack, x, y, w, h, 2, accentRgbLike);
        AbstractGuiUtils.fillOutLine(stack, x + 2, y + 2, w - 4, h - 4, 1, t.border());
    }

    private static void drawSlotCellGrid(MatrixStack stack, int guiLeft, int guiTop, int chestRows, int playerOff, BaniraColorConfig t) {
        int slotLine = blendArgb(t.border(), 0.42f);
        int slotFill = blendArgb(t.bgQuaternary(), 0.55f);
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
        AbstractGuiUtils.fill(stack, x + 1, y + 1, SLOT_STEP - 2, SLOT_STEP - 2, innerFill);
        AbstractGuiUtils.fillOutLine(stack, x, y, SLOT_STEP, SLOT_STEP, 1, line);
    }

    private static int blendArgb(int argb, float alphaMul) {
        int a = (argb >>> 24) & 0xFF;
        int na = Math.min(255, Math.round(a * alphaMul));
        if (na < 8) {
            na = 90;
        }
        return (na << 24) | (argb & 0xFFFFFF);
    }
}
