package xin.vanilla.aotake.enums;

/**
 * 进度条类型
 */
public enum EnumProgressBarType {
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

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }
}
