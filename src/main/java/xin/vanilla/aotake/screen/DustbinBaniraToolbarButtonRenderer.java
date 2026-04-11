package xin.vanilla.aotake.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.ShapeDrawArgs;
import xin.vanilla.banira.client.gui.widget.BaseShapeWidget;
import xin.vanilla.banira.client.gui.widget.ButtonWidget;
import xin.vanilla.banira.client.util.AbstractGuiUtils;


public final class DustbinBaniraToolbarButtonRenderer {

    /**
     * 预置图标着色
     */
    public enum IconTint {
        /**
         * 与 {@link ButtonWidget#presetStyleClose()} 一致的红系
         */
        CLEAR_ALL_CLOSE_RED,
        /**
         * 关闭叉形，橙/琥珀色以区别于「清空全部」
         */
        CLEAR_CACHE_CLOSE_ORANGE,
        /**
         * 减号，使用主题强调色
         */
        CLEAR_PAGE_MINUS_ACCENT,
        /**
         * 使用主题 {@link BaniraColorConfig#buttonPresetIconColor()} 系列
         */
        PRESET_DEFAULT
    }

    private static final float BUTTON_RADIUS = 2.0f;

    private DustbinBaniraToolbarButtonRenderer() {
    }

    public static void draw(MatrixStack stack, BaniraColorConfig theme,
                            int x, int y, int w, int h,
                            boolean hover,
                            boolean pressed,
                            boolean enabled,
                            ButtonWidget.PresetStyle preset,
                            IconTint tint) {
        int pad = Math.min(4, Math.max(1, (Math.min(w, h) - 6) / 2));
        int drawX = x;
        int drawY = y;
        int drawW = w;
        int drawH = h;

        int bg = pickState(theme.buttonBg(), theme.buttonBgHover(), theme.buttonBgPressed(), theme.buttonBgDisabled(),
                enabled, hover, pressed);
        int borderCol = pickState(theme.buttonBorder(), theme.buttonBorderHover(), theme.buttonBorderPressed(), theme.buttonBorderDisabled(),
                enabled, hover, pressed);

        ShapeDrawArgs rect = ShapeDrawArgs.rect(stack, drawX, drawY, drawW, drawH, bg);
        rect.rect().radius(BUTTON_RADIUS).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE);
        BaseShapeWidget.drawShape(rect);

        ShapeDrawArgs border = ShapeDrawArgs.rect(stack, drawX, drawY, drawW, drawH, borderCol);
        border.rect().radius(BUTTON_RADIUS).cornerMode(ShapeDrawArgs.RoundedCornerMode.FINE).border(1);
        BaseShapeWidget.drawShape(border);

        int contentX = drawX + pad;
        int contentY = drawY + pad;
        int aw = drawW - 2 * pad;
        int ah = drawH - 2 * pad;

        float stroke = preset == ButtonWidget.PresetStyle.CLOSE ? 2.0f : 1.5f;
        int icon = resolveIconColor(theme, tint, enabled, hover, pressed);
        drawPresetIcon(stack, preset, contentX, contentY, aw, ah, icon, stroke);
    }

    private static int pickState(int normal, int hoverC, int pressedC, int disabled,
                                 boolean enabled, boolean hover, boolean pressed) {
        if (!enabled) {
            return disabled;
        }
        if (pressed) {
            return pressedC;
        }
        if (hover) {
            return hoverC;
        }
        return normal;
    }

    private static int resolveIconColor(BaniraColorConfig t, IconTint tint, boolean enabled, boolean hover, boolean pressed) {
        if (!enabled) {
            switch (tint) {
                case CLEAR_ALL_CLOSE_RED:
                    return 0xFFB0BEC5;
                case CLEAR_CACHE_CLOSE_ORANGE:
                    return 0xFFBCAAA4;
                case CLEAR_PAGE_MINUS_ACCENT:
                case PRESET_DEFAULT:
                default:
                    return t.buttonPresetIconDisabledColor();
            }
        }
        if (pressed) {
            switch (tint) {
                case CLEAR_ALL_CLOSE_RED:
                    return 0xFFC62828;
                case CLEAR_CACHE_CLOSE_ORANGE:
                    return 0xFFE65100;
                case CLEAR_PAGE_MINUS_ACCENT:
                    return t.accentPressed();
                case PRESET_DEFAULT:
                default:
                    return t.buttonPresetIconPressedColor();
            }
        }
        if (hover) {
            switch (tint) {
                case CLEAR_ALL_CLOSE_RED:
                    return 0xFFFF5252;
                case CLEAR_CACHE_CLOSE_ORANGE:
                    return 0xFFFFB74D;
                case CLEAR_PAGE_MINUS_ACCENT:
                    return t.accentHover();
                case PRESET_DEFAULT:
                default:
                    return t.buttonPresetIconHoverColor();
            }
        }
        switch (tint) {
            case CLEAR_ALL_CLOSE_RED:
                return 0xFFE53935;
            case CLEAR_CACHE_CLOSE_ORANGE:
                return 0xFFFF9800;
            case CLEAR_PAGE_MINUS_ACCENT:
                return t.accent();
            case PRESET_DEFAULT:
            default:
                return t.buttonPresetIconColor();
        }
    }

    /**
     * 与 {@link ButtonWidget} 内 {@code drawPresetIcon} / {@code drawResetIcon} 几何一致。
     */
    private static void drawPresetIcon(MatrixStack stack, ButtonWidget.PresetStyle preset, int x, int y, int w, int h, int color, float iconStrokeWidth) {
        float iw = Math.max(0f, (float) w);
        float ih = Math.max(0f, (float) h);
        float size = Math.min(iw, ih);
        if (size < 2f) {
            return;
        }
        float cx = x + iw * 0.5f;
        float cy = y + ih * 0.5f;
        float r = Math.max(1f, size * 0.38f);
        if (r > size * 0.42f) {
            r = size * 0.42f;
        }
        float lw = iconStrokeWidth;

        switch (preset) {
            case CLOSE:
                AbstractGuiUtils.drawLine(stack, cx - r, cy - r, cx + r, cy + r, lw, color);
                AbstractGuiUtils.drawLine(stack, cx + r, cy - r, cx - r, cy + r, lw, color);
                break;
            case MINUS:
                AbstractGuiUtils.drawLine(stack, cx - r, cy, cx + r, cy, lw, color);
                break;
            case PLUS: {
                float plusR = Math.max(0.8f, r * 0.88f);
                float pr = plusR;
                if (pr + lw * 0.5f > size * 0.48f) {
                    pr = Math.max(0.6f, size * 0.48f - lw * 0.5f);
                }
                AbstractGuiUtils.drawLine(stack, cx - pr, cy, cx + pr, cy, lw, color);
                AbstractGuiUtils.drawLine(stack, cx, cy - pr, cx, cy + pr, lw, color);
                break;
            }
            case MAXIMIZE:
                ShapeDrawArgs.PolygonParams sq = new ShapeDrawArgs.PolygonParams()
                        .centerX(cx).centerY(cy).radius(r * 0.95f).sides(4).rotation(45).border(lw);
                AbstractGuiUtils.drawPolygonBorder(stack, sq, color);
                break;
            case ARROW_UP:
                AbstractGuiUtils.drawPolygon(stack, cx, cy, r, 3, -90, color);
                break;
            case ARROW_DOWN:
                AbstractGuiUtils.drawPolygon(stack, cx, cy, r, 3, 90, color);
                break;
            case ARROW_LEFT:
                AbstractGuiUtils.drawPolygon(stack, cx, cy, r, 3, 180, color);
                break;
            case ARROW_RIGHT:
                AbstractGuiUtils.drawPolygon(stack, cx, cy, r, 3, 0, color);
                break;
            case RESET:
                drawResetIcon(stack, cx, cy, r, lw, color);
                break;
            default:
                break;
        }
    }

    private static void drawResetIcon(MatrixStack stack, float cx, float cy, float r, float lw, int color) {
        float d = r * 1.375f;
        float triR = r * 0.65f;
        float xL = cx - d;
        float xR = cx + d;
        float y = cy;
        AbstractGuiUtils.drawLine(stack, xL + triR, y, xR - triR, y, lw, color);
        AbstractGuiUtils.drawPolygon(stack, xL + triR, y, triR, 3, 180, color);
        AbstractGuiUtils.drawPolygon(stack, xR - triR, y, triR, 3, 0, color);
    }
}
