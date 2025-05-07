package xin.vanilla.aotake;

import com.mojang.brigadier.CommandDispatcher;
import lombok.Getter;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.network.SplitPacket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod(AotakeSweep.MODID)
public class AotakeSweep {

    public final static String DEFAULT_COMMAND_PREFIX = "aotake";

    public static final String MODID = "aotake_sweep";

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 服务端实例
     */
    @Getter
    private static MinecraftServer serverInstance;

    /**
     * 服务器是否已启动
     */
    private boolean serverStarted = false;

    /**
     * 分片网络包缓存
     */
    @Getter
    private static final Map<String, List<? extends SplitPacket>> packetCache = new ConcurrentHashMap<>();

    /**
     * 命令调度器
     */
    private CommandDispatcher<CommandSource> dispatcher;

    public AotakeSweep() {

        // 注册网络通道
        // ModNetworkHandler.registerPackets();

        // 注册服务器启动和关闭事件
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // 注册当前实例到事件总线
        MinecraftForge.EVENT_BUS.register(this);

        // 注册服务端配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SERVER_CONFIG);

        // 注册客户端设置事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    /**
     * 客户端设置阶段事件
     */
    @SubscribeEvent
    public void onClientSetup(final FMLClientSetupEvent event) {
        // 注册键绑定
        LOGGER.debug("Registering key bindings");
        // ClientEventHandler.registerKeyBindings();
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        serverInstance = event.getServer();
    }

    private void onServerStarted(FMLServerStartedEvent event) {
        this.serverStarted = true;
        this.registerCommands();
    }

    private void onServerStopping(FMLServerStoppingEvent event) {
        this.serverStarted = false;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        this.dispatcher = event.getDispatcher();
        this.registerCommands();
    }

    private void registerCommands() {
        if (serverStarted && dispatcher != null) {
            LOGGER.debug("Registering commands");
            // 注册指令
            // AotakeCommand.register(this.dispatcher);
        }
    }

}
