package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

/**
 * 垃圾箱溢出时的处理方式
 */
public enum EnumOverflowMode implements IEnumDescribable {
    /**
     * 保留
     */
    KEEP,
    /**
     * 移除
     */
    REMOVE,
    /**
     * 随机替换
     */
    REPLACE,
    ;


    public static EnumOverflowMode valueOf(Object obj) {
        if (obj instanceof EnumOverflowMode) return (EnumOverflowMode) obj;
        if (obj instanceof String) {
            for (EnumOverflowMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumOverflowMode valueOfOrDefault(Object obj) {
        EnumOverflowMode value = valueOf(obj);
        return value == null ? KEEP : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
