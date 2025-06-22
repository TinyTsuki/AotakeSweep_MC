package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.network.SplitPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod(AotakeSweep.MODID)
public class AotakeSweep {

    public final static String DEFAULT_COMMAND_PREFIX = "aotake";

    public static final String MODID = "aotake_sweep";
    public static final String ARTIFACT_ID = "xin.vanilla";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 服务端实例
     */
    @Getter
    private static MinecraftServer serverInstance;

    /**
     * 已安装mod的玩家列表</br>
     * 玩家UUID:是否已同步数据</br>
     * 在该map的玩家都为已安装mod</br>
     * 布尔值为false时为未同步数据，将会在玩家tick事件中检测并同步数据
     */
    @Getter
    private static final Set<String> customConfigStatus = new HashSet<>();

    /**
     * 分片网络包缓存
     */
    @Getter
    private static final Map<String, List<? extends SplitPacket>> packetCache = new ConcurrentHashMap<>();

    /**
     * 玩家当前浏览的垃圾箱页数
     */
    @Getter
    private static final Map<String, Integer> playerDustbinPage = new ConcurrentHashMap<>();

    public static final Random RANDOM = new Random();

    @Getter
    @Setter
    private static boolean disable = false;

    // public static final Item JUNK_BALL = new JunkBall();

    public AotakeSweep(FMLJavaModLoadingContext context) {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册配置
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);

        // 注册客户端设置事件
        context.getModEventBus().addListener(this::onClientSetup);
        // 注册公共设置事件
        context.getModEventBus().addListener(this::onCommonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.getModEventBus().addListener(ClientModEventHandler::registerKeyBindings);
        }
    }

    /**
     * 客户端设置阶段事件
     */
    @SubscribeEvent
    public void onClientSetup(final FMLClientSetupEvent event) {
    }

    /**
     * 公共设置阶段事件
     */
    @SubscribeEvent
    public void onCommonSetup(final FMLCommonSetupEvent event) {
        CustomConfig.loadCustomConfig(false);
    }

    private void onServerStarting(ServerStartingEvent event) {
        AotakeSweep.serverInstance = event.getServer();
    }

    private void onServerStarted(ServerStartedEvent event) {
    }

    private void onServerStopping(ServerStoppingEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        AotakeCommand.register(event.getDispatcher());
    }


    // region 资源ID

    public static ResourceLocation emptyResource() {
        return createResource("", "");
    }

    public static ResourceLocation createResource(String path) {
        return createResource(AotakeSweep.MODID, path);
    }

    public static ResourceLocation createResource(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation parseResource(String location) {
        return ResourceLocation.tryParse(location);
    }

    // endregion 资源ID


    // region 外部方法
    public void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }
    // endregion 外部方法

}
