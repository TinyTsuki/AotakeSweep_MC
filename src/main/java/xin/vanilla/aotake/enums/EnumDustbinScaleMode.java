package xin.vanilla.aotake.enums;

/**
 * 垃圾箱 GUI 纹理缩放模式
 */
public enum EnumDustbinScaleMode {
    /**
     * 以宽度为基准缩放，高度按比例
     */
    WIDTH,
    /**
     * 以高度为基准缩放，宽度按比例
     */
    HEIGHT,
    /**
     * 自适应：取较小缩放比以完整显示
     */
    FIT,
    /**
     * 不缩放：按纹理原始尺寸绘制
     */
    NONE;

    private static final EnumDustbinScaleMode[] VALUES = values();

    public static EnumDustbinScaleMode fromString(String name) {
        if (name == null || name.isEmpty()) return FIT;
        String upper = name.toUpperCase().trim();
        for (EnumDustbinScaleMode mode : VALUES) {
            if (mode.name().equals(upper)) return mode;
        }
        return FIT;
    }

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty()) return false;
        String upper = name.toUpperCase().trim();
        for (EnumDustbinScaleMode mode : VALUES) {
            if (mode.name().equals(upper)) return true;
        }
        return false;
    }
}
