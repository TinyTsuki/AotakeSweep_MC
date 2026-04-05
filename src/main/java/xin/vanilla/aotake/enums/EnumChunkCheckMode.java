package xin.vanilla.aotake.enums;

import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.IEnumDescribable;
import xin.vanilla.banira.common.util.EnumDescriptionHelper;

public enum EnumChunkCheckMode implements IEnumDescribable {
    DEFAULT,
    ADVANCED,
    ;

    public static EnumChunkCheckMode valueOf(Object obj) {
        if (obj instanceof EnumChunkCheckMode) return (EnumChunkCheckMode) obj;
        if (obj instanceof String) {
            for (EnumChunkCheckMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumChunkCheckMode valueOfOrDefault(Object obj) {
        EnumChunkCheckMode value = valueOf(obj);
        return value == null ? DEFAULT : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

    @Override
    public Component enumDescription() {
        return EnumDescriptionHelper.describeEnum(AotakeComponent.get(), this);
    }
}
