package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.network.SplitPacket;
import xin.vanilla.aotake.util.AotakeScheduler;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.EntitySweeper;

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
    private final static KeyValue<MinecraftServer, Boolean> serverInstance = new KeyValue<>(null, true);

    /**
     * 已安装mod的玩家列表
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

    // public static final Item JUNK_BALL = new JunkBall();

    public AotakeSweep() {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);
        // 注册调度器
        MinecraftForge.EVENT_BUS.register(AotakeScheduler.class);

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);

        // 注册客户端设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        // 注册公共设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
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

    /**
     * 公共设置阶段事件
     */
    public void onCommonSetup(final FMLCommonSetupEvent event) {
        CustomConfig.loadCustomConfig(false);
    }

    private void onServerStarting(ServerStartingEvent event) {
        AotakeSweep.serverInstance.setKey(event.getServer()).setValue(true);
    }

    private void onServerStarted(ServerStartedEvent event) {
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AotakeSweep.serverInstance.setValue(false);
        PlayerSweepData.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.debug("Registering commands");
        AotakeCommand.register(event.getDispatcher());
    }

    public void onConfigReload(ModConfigEvent event) {
        try {
            if (event.getConfig().getSpec() == ServerConfig.SERVER_CONFIG && serverInstance.val()) {
                ServerConfig.bake();
                CommandUtils.configKeyMapCache.clear();
            }
        } catch (Exception ignored) {
        }
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
