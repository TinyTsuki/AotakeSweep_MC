package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

public enum EnumDustbinMode implements IEnumDescribable {
    VIRTUAL,
    BLOCK,
    VIRTUAL_BLOCK,
    BLOCK_VIRTUAL,
    ;

    public static EnumDustbinMode valueOf(Object obj) {
        if (obj instanceof EnumDustbinMode) return (EnumDustbinMode) obj;
        if (obj instanceof String) {
            for (EnumDustbinMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumDustbinMode valueOfOrDefault(Object obj) {
        EnumDustbinMode value = valueOf(obj);
        return value == null ? VIRTUAL : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
