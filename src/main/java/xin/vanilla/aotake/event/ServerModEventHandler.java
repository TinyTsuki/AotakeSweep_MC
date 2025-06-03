package xin.vanilla.aotake.event;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;

/**
 * 服务端 Mod事件处理器
 */
@EventBusSubscriber(modid = AotakeSweep.MODID, value = Dist.DEDICATED_SERVER, bus = EventBusSubscriber.Bus.MOD)
public class ServerModEventHandler {
    private static final Logger LOGGER = LogManager.getLogger();


}
