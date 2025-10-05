package xin.vanilla.aotake.enums;

public enum EnumListType {
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

}
