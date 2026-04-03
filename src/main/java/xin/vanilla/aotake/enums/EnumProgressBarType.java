package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

import java.util.Arrays;

/**
 * 进度条类型
 */
public enum EnumProgressBarType implements IEnumDescribable {
    LEAF,
    POLE,
    TEXT,
    ;

    public static EnumProgressBarType valueOf(Object obj) {
        if (obj instanceof EnumProgressBarType) return (EnumProgressBarType) obj;
        if (obj instanceof String) {
            for (EnumProgressBarType value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumProgressBarType valueOfOrDefault(Object obj) {
        EnumProgressBarType value = valueOf(obj);
        return value == null ? LEAF : value;
    }

    public static String[] names() {
        return Arrays.stream(EnumProgressBarType.values()).map(EnumProgressBarType::name).toArray(String[]::new);
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
