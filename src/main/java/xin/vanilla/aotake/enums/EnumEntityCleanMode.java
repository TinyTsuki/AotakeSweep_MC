package xin.vanilla.aotake.enums;

public enum EnumEntityCleanMode {
    DEFAULT,
    CHUNK,
    ;

    public static EnumEntityCleanMode valueOf(Object obj) {
        if (obj instanceof EnumEntityCleanMode) return (EnumEntityCleanMode) obj;
        if (obj instanceof String) {
            for (EnumEntityCleanMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumEntityCleanMode valueOfOrDefault(Object obj) {
        EnumEntityCleanMode value = valueOf(obj);
        return value == null ? DEFAULT : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

}
