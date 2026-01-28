package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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
import xin.vanilla.aotake.util.AotakeScheduler;
import xin.vanilla.aotake.util.CommandUtils;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    public AotakeSweep(FMLJavaModLoadingContext context) {

        // 注册网络通道
        ModNetworkHandler.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);
        // 注册调度器
        MinecraftForge.EVENT_BUS.register(AotakeScheduler.class);

        // 注册配置
        context.registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        context.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_CONFIG);

        // 注册客户端设置事件
        context.getModEventBus().addListener(this::onClientSetup);
        // 注册公共设置事件
        context.getModEventBus().addListener(this::onCommonSetup);
        // 注册配置文件重载事件
        context.getModEventBus().addListener(this::onConfigReload);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.getModEventBus().addListener(ClientModEventHandler::registerKeyBindings);
        }
    }

    /**
     * 客户端设置阶段事件
     */
    public void onClientSetup(final FMLClientSetupEvent event) {
    }

    /**
     * 公共设置阶段事件
     */
    public void onCommonSetup(final FMLCommonSetupEvent event) {
        CustomConfig.loadCustomConfig(false);
    }

    private void onServerStarting(ServerStartingEvent event) {
        entitySweeper.clear();
        AotakeSweep.serverInstance.setKey(event.getServer()).setValue(true);
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
                entityFilter.clear();
                CommandUtils.configKeyMapCache.clear();
            }
        } catch (Exception ignored) {
        }
    }

    // region 资源ID

    public static ResourceLocation emptyIdentifier() {
        return createIdentifier("", "");
    }

    public static ResourceLocation createIdentifier(String path) {
        return createIdentifier(AotakeSweep.MODID, path);
    }

    public static ResourceLocation createIdentifier(String namespace, String path) {
        return ResourceLocation.tryBuild(namespace, path);
    }

    public static ResourceLocation parseIdentifier(String location) {
        return ResourceLocation.tryParse(location);
    }

    // endregion 资源ID


    // region 外部方法
    @SuppressWarnings("unused")
    public void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }
    // endregion 外部方法

}
