package xin.vanilla.aotake.event;

import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.api.client.BaniraInput;
import xin.vanilla.banira.api.client.BaniraKeyHandle;
import xin.vanilla.banira.api.client.notification.BaniraClientNotificationTypes;

/**
 * 客户端：注册 Banira 键位与通知类型。
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
    public static BaniraKeyHandle PROGRESS_KEY = BaniraInput.registerKey(AotakeSweep.MODID, "progress", GLFW.GLFW_KEY_F9);

    private ClientModEventHandler() {
    }

    /**
     * 由加载器客户端入口触发按键与通知类型注册。
     */
    public static void bootstrap() {
        for (String id : AotakeNotificationTypes.ALL_TYPE_IDS) {
            BaniraClientNotificationTypes.register(id);
        }
    }
}
