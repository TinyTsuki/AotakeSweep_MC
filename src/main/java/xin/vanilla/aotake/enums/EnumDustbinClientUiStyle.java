package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

import java.util.Locale;

/**
 * 垃圾箱客户端界面样式
 */
public enum EnumDustbinClientUiStyle implements IEnumDescribable {
    /**
     * 原版箱子界面与原版风格侧栏按钮
     */
    VANILLA,
    /**
     * 自定义纹理背景与图标按钮
     */
    TEXTURED,
    /**
     * 使用主题色绘制容器与侧栏按钮
     */
    BANIRA_THEME;

    private static final EnumDustbinClientUiStyle[] VALUES = values();

    public static EnumDustbinClientUiStyle valueOf(Object obj) {
        if (obj instanceof EnumDustbinClientUiStyle) return (EnumDustbinClientUiStyle) obj;
        if (obj instanceof String) {
            for (EnumDustbinClientUiStyle value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return TEXTURED;
    }

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty()) return false;
        String upper = name.toUpperCase(Locale.ROOT).trim();
        for (EnumDustbinClientUiStyle v : VALUES) {
            if (v.name().equals(upper)) return true;
        }
        return false;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
