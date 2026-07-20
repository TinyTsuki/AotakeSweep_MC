package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.server.dev.AotakeNetworkSmokeServerRunner;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.network.ModLoadedPresence;
import xin.vanilla.banira.common.util.BaniraEventBus;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PacketUtils;

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
     * 区块清理暂存箱：当前打开的 vaultId 与页码（供客户端翻页同步）
     */
    @Getter
    private static final Map<String, Integer> playerChunkVaultPage = new ConcurrentHashMap<>();
    @Getter
    private static final Map<String, String> playerChunkVaultId = new ConcurrentHashMap<>();

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

    /**
     * 客户端：由 {@link SweepDataSyncToClient} 写入，供偏好界面读取（默认与 {@link xin.vanilla.aotake.data.player.PlayerSweepData} 一致）。
     */
    @Getter
    private static volatile boolean clientCachedShowSweepResult = true;
    @Getter
    private static volatile boolean clientCachedEnableWarningVoice = true;

    public static void setClientCachedPlayerSweepPrefs(boolean showSweepResult, boolean enableWarningVoice) {
        AotakeSweep.clientCachedShowSweepResult = showSweepResult;
        AotakeSweep.clientCachedEnableWarningVoice = enableWarningVoice;
    }

    public static final Random RANDOM = new Random();

    @Getter
    @Setter
    private static boolean disable = false;

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();
    @Getter
    private static final EntityFilter entityFilter = new EntityFilter();

    public AotakeSweep() {
        // Forge 会在 common setup 前加载 COMMON/CLIENT 配置，必须在构造阶段完成注册。
        BaniraConfig.register(CommonConfig.class, MODID);
        BaniraConfig.register(ClientConfig.class, MODID);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);

        BaniraEventBus.Server.onStarting(server -> entitySweeper.clear());
        AotakeNetworkSmokeServerRunner.register();
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AotakeCommand.register(event.getDispatcher()));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> EventHandlerProxy.onServerTick(event));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.WorldTickEvent event) -> EventHandlerProxy.onWorldTick(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> EventHandlerProxy.onPlayerUseItem(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> EventHandlerProxy.onRightBlock(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> EventHandlerProxy.onRightEntity(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> EventHandlerProxy.onPlayerLoggedIn(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> EventHandlerProxy.onPlayerLoggedOut(event));

        // 注册配置文件重载事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.addListener(ChunkVaultSession::onContainerClose);

        DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> xin.vanilla.aotake.client.AotakeClientBootstrap::init);
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        NetworkInit.registerPackets();
        event.enqueueWork(() -> {
            AotakeNotificationTypes.registerAllOnServer();
            ModLoadedPresence.register(MODID, player -> {
                if (!(player instanceof ServerPlayerEntity)) return;
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(serverPlayer), serverPlayer);
                CommandUtils.refreshPermission(serverPlayer);
            });
        });
    }

    public void onConfigReload(ModConfig.ModConfigEvent event) {
        try {
            ModConfig cfg = event.getConfig();
            ConfigHolder commonHolder = BaniraConfig.holder(CommonConfig.class);
            if (commonHolder != null && cfg.getFileName().contains(commonHolder.getConfigName()) && BaniraServer.isRunning()) {
                AotakeUtils.clearEntityFilterCaches();
            }
        } catch (Exception ignored) {
        }
    }

}
