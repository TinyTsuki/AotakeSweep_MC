package xin.vanilla.aotake.event;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeSweep;

/**
 * 客户端 Mod事件处理器
 */
@EventBusSubscriber(modid = AotakeSweep.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String CATEGORIES = "key.aotake_sweep.categories";

    /**
     * 垃圾箱快捷键
     */
    public static KeyMapping DUSTBIN_KEY = new KeyMapping("key.aotake_sweep.open_dustbin",
            GLFW.GLFW_KEY_UNKNOWN, CATEGORIES);
    /**
     * 垃圾箱上页快捷键
     */
    public static KeyMapping DUSTBIN_PRE_KEY = new KeyMapping("key.aotake_sweep.open_dustbin_pre",
            GLFW.GLFW_KEY_LEFT, CATEGORIES);
    /**
     * 垃圾箱下页快捷键
     */
    public static KeyMapping DUSTBIN_NEXT_KEY = new KeyMapping("key.aotake_sweep.open_dustbin_next",
            GLFW.GLFW_KEY_RIGHT, CATEGORIES);

    /**
     * 注册键绑定
     */
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        // 注册键绑定
        LOGGER.debug("Registering key bindings");
        event.register(DUSTBIN_KEY);
        event.register(DUSTBIN_PRE_KEY);
        event.register(DUSTBIN_NEXT_KEY);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
    }
}
