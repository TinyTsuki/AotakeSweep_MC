package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerDataAttachment;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.network.SplitPacket;
import xin.vanilla.aotake.util.AotakeScheduler;
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

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();

    // public static final Item JUNK_BALL = new JunkBall();

    public AotakeSweep(IEventBus modEventBus, ModContainer modContainer) {

        // 注册网络通道
        modEventBus.addListener(ModNetworkHandler::registerPackets);

        // 注册服务器启动和关闭事件
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到事件总线
        NeoForge.EVENT_BUS.register(this);
        // 注册调度器
        NeoForge.EVENT_BUS.register(AotakeScheduler.class);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.COMMON_CONFIG);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);
        // 注册数据附件
        PlayerDataAttachment.ATTACHMENT_TYPES.register(modEventBus);

        // 注册客户端设置事件
        modEventBus.addListener(this::onClientSetup);
        // 注册公共设置事件
        modEventBus.addListener(this::onCommonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientModEventHandler::registerKeyBindings);
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

    @SubscribeEvent
    private void onServerStarting(ServerStartingEvent event) {
        AotakeSweep.serverInstance = event.getServer();
    }

    @SubscribeEvent
    private void onServerStarted(ServerStartedEvent event) {
    }

    @SubscribeEvent
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
        return ResourceLocation.tryBuild(namespace, path);
    }

    public static ResourceLocation parseResource(String location) {
        return ResourceLocation.tryParse(location);
    }

    // endregion 资源ID

}
