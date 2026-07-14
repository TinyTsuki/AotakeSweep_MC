package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.util.BaniraEventBus;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.platform.BaniraConfigHandle;

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
     * 客户端：由 {@link SweepDataSyncToClient} 写入，供偏好界面读取。
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

    public AotakeSweep(IEventBus modEventBus, ModContainer modContainer) {
        // NeoForge 与 Forge 都要求配置在加载配置文件前完成注册。
        BaniraConfigs.register(CommonConfig.class, MODID);
        BaniraConfigs.register(ClientConfig.class, MODID);
        NetworkInit.registerPackets();
        modEventBus.addListener(this::onCommonSetup);

        BaniraEventBus.Server.onStarting(server -> entitySweeper.clear());
        BaniraEventBus.Commands.onRegister(event -> AotakeCommand.register(event.getDispatcher()));

        BaniraEventBus.Server.onTick(EventHandlerProxy::onServerTick);
        BaniraEventBus.WorldEvents.onTick(EventHandlerProxy::onWorldTick);
        // Banira 的泛型玩家事件只覆盖已显式订阅的子类，拉弓事件需由当前加载器接入。
        NeoForge.EVENT_BUS.addListener((ArrowNockEvent event) -> EventHandlerProxy.onPlayerUseItem(event));
        BaniraEventBus.Interaction.onRightClickItem(EventHandlerProxy::onPlayerUseItem);
        BaniraEventBus.Interaction.onRightClickBlock(event -> {
            EventHandlerProxy.onRightBlock(event);
            EventHandlerProxy.onPlayerUseItem(event);
        });
        BaniraEventBus.Interaction.onEntityInteractSpecific(EventHandlerProxy::onRightEntity);
        BaniraEventBus.Player.onLoggedIn(player -> EventHandlerProxy.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player)));
        BaniraEventBus.Player.onLoggedOut(player -> EventHandlerProxy.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player)));

        modEventBus.addListener(this::onConfigReload);
        NeoForge.EVENT_BUS.addListener(ChunkVaultSession::onContainerClose);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            xin.vanilla.aotake.client.AotakeClientBootstrap.init();
        }
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AotakeNotificationTypes.registerAllOnServer();
            BaniraModPresence.register(MODID, player -> {
                if (!(player instanceof ServerPlayer)) return;
                ServerPlayer serverPlayer = (ServerPlayer) player;
                PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(serverPlayer), serverPlayer);
                CommandUtils.refreshPermission(serverPlayer);
            });
        });
    }

    public void onConfigReload(ModConfigEvent event) {
        try {
            ModConfig cfg = event.getConfig();
            BaniraConfigHandle commonHolder = BaniraConfigs.handle(CommonConfig.class);
            if (commonHolder != null && cfg.getFileName().contains(commonHolder.getConfigName()) && BaniraServer.isRunning()) {
                AotakeUtils.clearEntityFilterCaches();
            }
        } catch (Exception ignored) {
        }
    }

}
