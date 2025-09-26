package xin.vanilla.aotake.enums;

/**
 * 自清理模式
 */
public enum EnumSelfCleanMode {
    /**
     * 无
     */
    NONE,
    /**
     * 扫地前清空
     */
    SWEEP_CLEAR,
    /**
     * 扫地时随机删除
     */
    SWEEP_DELETE,
    /**
     * 定时清空
     */
    SCHEDULED_CLEAR,
    /**
     * 定时随机删除
     */
    SCHEDULED_DELETE,
    ;

    public static EnumSelfCleanMode valueOf(Object obj) {
        if (obj instanceof EnumSelfCleanMode) return (EnumSelfCleanMode) obj;
        if (obj instanceof String) {
            for (EnumSelfCleanMode value : values()) {
                if (value.name().equalsIgnoreCase((String) obj)) {
                    return value;
                }
            }
        }
        return null;
    }

    public static EnumSelfCleanMode valueOfOrDefault(Object obj) {
        EnumSelfCleanMode value = valueOf(obj);
        return value == null ? NONE : value;
    }

    public static boolean isValid(Object obj) {
        return valueOf(obj) != null;
    }

}
