package xin.vanilla.aotake.notification;

import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.banira.common.enums.EnumMoveType;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;
import xin.vanilla.banira.common.enums.EnumPosition;
import xin.vanilla.banira.common.notification.ServerNotificationTypeRegistry;

/**
 * 竹叶清通知类型ID
 */
public final class AotakeNotificationTypes {

    private static final String P = AotakeSweep.MODID + ".";

    /**
     * 自动清理倒计时提示（原操作栏节奏；无 Banira 客户端时回退为操作栏）
     */
    public static final String SWEEP_COUNTDOWN = P + "sweep_countdown";

    /**
     * 扫地完成/失败等带可点击链接的详细结果（聊天栏式交互）
     */
    public static final String SWEEP_RESULT_INTERACTIVE = P + "sweep_result_interactive";

    /**
     * 扫地结果摘要（关闭详细聊天时的轻量提示）
     */
    public static final String SWEEP_RESULT_COMPACT = P + "sweep_result_compact";

    /**
     * 区块实体过多：管理员完整提示（坐标、复制、关闭按钮等）
     */
    public static final String CHUNK_CHECK_INTERACTIVE = P + "chunk_check_interactive";

    /**
     * 区块实体过多：非管理员简短提示
     */
    public static final String CHUNK_CHECK_COMPACT = P + "chunk_check_compact";

    /**
     * 捕获/释放实体等即时操作反馈
     */
    public static final String ENTITY_TOOL_FEEDBACK = P + "entity_tool_feedback";

    /**
     * 垃圾箱：空服提示、清空/倒掉广播等
     */
    public static final String DUSTBIN = P + "dustbin";

    /**
     * 区块超载暂存：{@code list} 子命令输出（与垃圾箱等其它通知分流，便于客户端单独配置）
     */
    public static final String CHUNK_VAULT_LIST = P + "chunk_vault_list";

    /**
     * 管理类广播：模组开关、缓存清理、延后清理、掉落物清理等
     */
    public static final String ADMIN_BROADCAST = P + "admin_broadcast";

    /**
     * 玩家个人偏好：显示扫地结果、提示音开关等
     */
    public static final String PLAYER_PREFERENCE = P + "player_preference";

    public static final String[] ALL_TYPE_IDS = {
            SWEEP_COUNTDOWN,
            SWEEP_RESULT_INTERACTIVE,
            SWEEP_RESULT_COMPACT,
            CHUNK_CHECK_INTERACTIVE,
            CHUNK_CHECK_COMPACT,
            ENTITY_TOOL_FEEDBACK,
            DUSTBIN,
            CHUNK_VAULT_LIST,
            ADMIN_BROADCAST,
            PLAYER_PREFERENCE,
    };

    public static void registerAllOnServer() {
        EnumNotificationTypeDisplayMode vanillaChat = EnumNotificationTypeDisplayMode.VANILLA_CHAT;
        ServerNotificationTypeRegistry.register(SWEEP_COUNTDOWN, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(SWEEP_RESULT_INTERACTIVE, EnumPosition.TOP_CENTER, EnumMoveType.AUTO, vanillaChat);
        ServerNotificationTypeRegistry.register(SWEEP_RESULT_COMPACT, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(CHUNK_CHECK_INTERACTIVE, EnumPosition.TOP_CENTER, EnumMoveType.AUTO, vanillaChat);
        ServerNotificationTypeRegistry.register(CHUNK_CHECK_COMPACT, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(ENTITY_TOOL_FEEDBACK, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(DUSTBIN, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(CHUNK_VAULT_LIST, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(ADMIN_BROADCAST, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
        ServerNotificationTypeRegistry.register(PLAYER_PREFERENCE, EnumPosition.TOP_CENTER, EnumMoveType.AUTO);
    }

    private AotakeNotificationTypes() {
    }
}
