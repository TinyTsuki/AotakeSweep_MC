package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.KeyValue;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Mod(AotakeSweep.MODID)
public class AotakeSweep {

    public final static String DEFAULT_COMMAND_PREFIX = "aotake";

    public static final String MODID = "aotake_sweep";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 玩家当前浏览的垃圾箱页数
     */
    @Getter
    private static final Map<String, Integer> playerDustbinPage = new ConcurrentHashMap<>();

    /**
     * 客户端-服务器时间
     */
    @Getter
    private static final KeyValue<Long, Long> clientServerTime = new KeyValue<>(0L, 0L);

    /**
     * 扫地间隔-下次扫地时间
     */
    @Getter
    private static final KeyValue<Long, Long> sweepTime = new KeyValue<>(0L, 0L);


    public static final Random RANDOM = new Random();

    @Getter
    @Setter
    private static boolean disable = false;

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();
    @Getter
    private static final EntityFilter entityFilter = new EntityFilter();

    public AotakeSweep() {
        // 注册网络通道
        NetworkInit.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);

        // 注册客户端设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        // 注册配置文件重载事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
    }

    /**
     * 客户端设置阶段事件
     */
    public void onClientSetup(final FMLClientSetupEvent event) {
        // 注册键绑定
        LOGGER.debug("Registering key bindings");
        ClientModEventHandler.registerKeyBindings();
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        entitySweeper.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        AotakeCommand.register(event.getDispatcher());
    }

    public void onConfigReload(ModConfig.ModConfigEvent event) {
        try {
            if (event.getConfig().getSpec() == CommonConfig.COMMON_CONFIG && BaniraCodex.serverInstance().val()) {
                entityFilter.clear();
            }
        } catch (Exception ignored) {
        }
    }

}
