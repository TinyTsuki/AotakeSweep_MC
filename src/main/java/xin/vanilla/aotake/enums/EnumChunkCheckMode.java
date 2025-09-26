package xin.vanilla.aotake.enums;

public enum EnumChunkCheckMode {
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

}
