package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

public enum EnumListType implements IEnumDescribable {
    WHITE,
    BLACK,
    ;

    public static EnumListType valueOf(Object obj) {
        if (obj instanceof EnumListType) return (EnumListType) obj;
        if (obj instanceof String) {
            for (EnumListType value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumListType valueOfOrDefault(Object obj) {
        EnumListType value = valueOf(obj);
        return value == null ? WHITE : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
