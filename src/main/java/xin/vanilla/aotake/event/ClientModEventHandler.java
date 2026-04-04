package xin.vanilla.aotake.event;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.util.BaniraKeyBindings;

/**
 * 客户端：Banira 键位入队 + {@link BaniraClientEventHub} 回调注册（不在此类上使用 Forge {@code @SubscribeEvent}）
 */
public final class ClientModEventHandler {

    /**
     * 垃圾箱快捷键
     */
    public static KeyBinding DUSTBIN_KEY = BaniraKeyBindings.register(AotakeSweep.MODID, "open_dustbin", GLFW.GLFW_KEY_UNKNOWN);
    /**
     * 垃圾箱上页快捷键
     */
    public static KeyBinding DUSTBIN_PRE_KEY = BaniraKeyBindings.register(AotakeSweep.MODID, "open_dustbin_pre", GLFW.GLFW_KEY_LEFT);
    /**
     * 垃圾箱下页快捷键
     */
    public static KeyBinding DUSTBIN_NEXT_KEY = BaniraKeyBindings.register(AotakeSweep.MODID, "open_dustbin_next", GLFW.GLFW_KEY_RIGHT);

    /**
     * 切换进度条显示按键
     */
    public static KeyBinding PROGRESS_KEY = BaniraKeyBindings.register(AotakeSweep.MODID, "progress", GLFW.GLFW_KEY_TAB);

    private ClientModEventHandler() {
    }

    /**
     * 由主模组构造函数经 {@link net.minecraftforge.fml.DistExecutor} 在客户端触发类初始化
     */
    public static void bootstrap() {
    }
}
