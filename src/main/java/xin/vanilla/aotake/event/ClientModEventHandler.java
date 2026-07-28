package xin.vanilla.aotake.event;

import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.api.client.BaniraInput;
import xin.vanilla.banira.api.client.BaniraKeyHandle;
import xin.vanilla.banira.api.client.notification.BaniraClientNotificationTypes;

/**
 * 客户端：Banira 键位入队与稳定事件回调注册（不在此类上使用 Forge {@code @SubscribeEvent}）
 */
public final class ClientModEventHandler {

    /**
     * 垃圾箱快捷键
     */
    public static BaniraKeyHandle DUSTBIN_KEY = BaniraInput.registerKey(AotakeSweep.MODID, "open_dustbin", GLFW.GLFW_KEY_UNKNOWN);
    /**
     * 垃圾箱上页快捷键
     */
    public static BaniraKeyHandle DUSTBIN_PRE_KEY = BaniraInput.registerKey(AotakeSweep.MODID, "open_dustbin_pre", GLFW.GLFW_KEY_LEFT);
    /**
     * 垃圾箱下页快捷键
     */
    public static BaniraKeyHandle DUSTBIN_NEXT_KEY = BaniraInput.registerKey(AotakeSweep.MODID, "open_dustbin_next", GLFW.GLFW_KEY_RIGHT);

    /**
     * 切换进度条显示按键
     */
    public static BaniraKeyHandle PROGRESS_KEY = BaniraInput.registerKey(AotakeSweep.MODID, "progress", GLFW.GLFW_KEY_TAB);

    private ClientModEventHandler() {
    }

    /**
     * 由主模组构造函数经 {@link net.minecraftforge.fml.DistExecutor} 在客户端触发类初始化
     */
    public static void register() {
        registerNotificationMetadata();
    }

    private static void registerNotificationMetadata() {
        BaniraClientNotificationTypes.registerModDisplayName(AotakeSweep.MODID, AotakeComponent.get().transClientAuto("mod_name"));
        registerNotificationType(AotakeNotificationTypes.SWEEP_COUNTDOWN, "notification_type_sweep_countdown");
        registerNotificationType(AotakeNotificationTypes.SWEEP_RESULT_INTERACTIVE, "notification_type_sweep_result_interactive");
        registerNotificationType(AotakeNotificationTypes.SWEEP_RESULT_COMPACT, "notification_type_sweep_result_compact");
        registerNotificationType(AotakeNotificationTypes.CHUNK_CHECK_INTERACTIVE, "notification_type_chunk_check_interactive");
        registerNotificationType(AotakeNotificationTypes.CHUNK_CHECK_COMPACT, "notification_type_chunk_check_compact");
        registerNotificationType(AotakeNotificationTypes.ENTITY_TOOL_FEEDBACK, "notification_type_entity_tool_feedback");
        registerNotificationType(AotakeNotificationTypes.DUSTBIN, "notification_type_dustbin");
        registerNotificationType(AotakeNotificationTypes.CHUNK_VAULT_LIST, "notification_type_chunk_vault_list");
        registerNotificationType(AotakeNotificationTypes.ADMIN_BROADCAST, "notification_type_admin_broadcast");
        registerNotificationType(AotakeNotificationTypes.PLAYER_PREFERENCE, "notification_type_player_preference");
        registerNotificationType(AotakeNotificationTypes.HELP, "notification_type_help");
    }

    /**
     * 将说明保留为客户端翻译组件，交由 Banira 按当前语言渲染。
     */
    private static void registerNotificationType(String typeId, String descriptionKey) {
        BaniraClientNotificationTypes.register(typeId, AotakeComponent.get().transClientAuto(descriptionKey));
    }
}
