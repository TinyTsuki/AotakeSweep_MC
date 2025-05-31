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
}
