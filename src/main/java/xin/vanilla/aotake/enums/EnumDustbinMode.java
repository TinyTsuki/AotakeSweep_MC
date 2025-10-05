package xin.vanilla.aotake.enums;

public enum EnumDustbinMode {
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

}
