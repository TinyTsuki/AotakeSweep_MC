package xin.vanilla.aotake.enums;

public enum EnumRotationCenter {
    TOP_LEFT,
    TOP_RIGHT,
    TOP_CENTER,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_CENTER,
    CENTER,
    ;

    public static EnumRotationCenter valueOf(Object obj) {
        if (obj instanceof EnumRotationCenter) return (EnumRotationCenter) obj;
        if (obj instanceof String) {
            for (EnumRotationCenter value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumRotationCenter valueOfOrDefault(Object obj) {
        EnumRotationCenter value = valueOf(obj);
        return value == null ? TOP_LEFT : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

}
