package xin.vanilla.aotake.event;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import xin.vanilla.aotake.AotakeSweep;

/**
 * 客户端 Mod事件处理器
 */
@Mod.EventBusSubscriber(modid = AotakeSweep.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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
    public static void registerKeyBindings() {
        ClientRegistry.registerKeyBinding(DUSTBIN_KEY);
        ClientRegistry.registerKeyBinding(DUSTBIN_PRE_KEY);
        ClientRegistry.registerKeyBinding(DUSTBIN_NEXT_KEY);
    }

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        EventHandlerProxy.registerCaps(event);
    }

}
