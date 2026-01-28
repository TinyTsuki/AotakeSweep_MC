package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CustomConfig;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.KeyValue;
import xin.vanilla.aotake.data.player.PlayerDataManager;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.ModNetworkHandler;
import xin.vanilla.aotake.util.AotakeScheduler;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Accessors(fluent = true)
public class AotakeSweep implements ModInitializer {

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


    @Override
    public void onInitialize() {

        // 注册网络通道
        ModNetworkHandler.registerServerPackets();

        // 注册服务器启动和关闭事件
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            entitySweeper.clear();
            AotakeSweep.serverInstance.setKey(server).setValue(true);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AotakeSweep.serverInstance.setValue(false);
            PlayerSweepData.clear();
        });

        // 注册事件
        ServerEventHandler.register();

        // 注册调度器
        ServerTickEvents.END_SERVER_TICK.register(AotakeScheduler::onServerTick);

        // 注册配置
        ServerConfig.register();
        CustomConfig.loadCustomConfig(false);
        PlayerDataManager.register();

        // 注册指令
        registerCommands();

    }


    public void registerCommands() {
        LOGGER.debug("Registering commands");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AotakeCommand.register(dispatcher));
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
    public static void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }
    // endregion 外部方法

}
