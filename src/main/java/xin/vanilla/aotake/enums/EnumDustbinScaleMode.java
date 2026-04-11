package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

/**
 * 垃圾箱 GUI 纹理缩放模式
 */
public enum EnumDustbinScaleMode implements IEnumDescribable {
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

    public static EnumDustbinScaleMode valueOf(Object obj) {
        if (obj instanceof EnumDustbinScaleMode) return (EnumDustbinScaleMode) obj;
        if (obj instanceof String) {
            for (EnumDustbinScaleMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
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

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
