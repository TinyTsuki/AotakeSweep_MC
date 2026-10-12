package xin.vanilla.aotake;

import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.forge.event.ForgeAotakeGameEventAdapter;
import xin.vanilla.aotake.internal.server.dev.AotakeNetworkSmokeServerRunner;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.internal.server.AotakeSweepRuntime;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.data.KeyValue;
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
    private static volatile boolean disable = false;

    public static void setDisable(boolean value) {
        if (disable == value) return;
        disable = value;
        xin.vanilla.aotake.network.SweepScheduleSync.broadcast(BaniraServer.currentAs(net.minecraft.server.MinecraftServer.class));
    }

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();

    public AotakeSweep(FMLJavaModLoadingContext context) {
        // Forge 配置必须在 CONFIG 加载阶段前注册；Banira 依赖会先完成平台安装。
        BaniraConfigs.register(CommonConfig.class, MODID);
        BaniraConfigs.register(ClientConfig.class, MODID);
        BaniraConfigs.onSaved(CommonConfig.class, changedPaths -> {
            if (changedPaths.stream().anyMatch(path -> path.equals("base.sweep.sweepInterval"))) {
                xin.vanilla.aotake.network.SweepScheduleSync.broadcast(BaniraServer.currentAs(net.minecraft.server.MinecraftServer.class));
            }
            boolean commandChanged = changedPaths.stream()
                    .anyMatch(path -> path.startsWith("command.") || path.startsWith("concise."));
            if (commandChanged) {
                AotakeCommand.refreshConfiguredCommands(BaniraServer.currentAs(net.minecraft.server.MinecraftServer.class));
            }
        });
        NetworkInit.registerPackets();
        context.getModEventBus().addListener(this::onCommonSetup);

        BaniraEventBus.Server.onStarting(event -> {
            net.minecraft.server.MinecraftServer server = event.serverAs(net.minecraft.server.MinecraftServer.class);
            entitySweeper.clear();
            xin.vanilla.aotake.event.EventHandlerProxy.resetCleanupState();
            AotakeRuleRuntime.start(server);
            AotakeSweepRuntime.start(server);
        });
        BaniraEventBus.Server.onStopping(event -> {
            net.minecraft.server.MinecraftServer server = event.serverAs(net.minecraft.server.MinecraftServer.class);
            try {
                try {
                    AotakeSweepRuntime.stop();
                } finally {
                    if (server != null) server.getAllLevels().forEach(EntitySweeper::flushPendingRemovals);
                }
            } finally {
                try {
                    entitySweeper.clear();
                } finally {
                    xin.vanilla.aotake.event.EventHandlerProxy.resetCleanupState();
                    AotakeRuleRuntime.stop();
                }
            }
        });
        ForgeAotakeGameEventAdapter.register(context.getModEventBus());
        AotakeNetworkSmokeServerRunner.register();

        // Client bootstrap 的方法签名会引用 Screen；safeRunWhenOn 会在专服校验该签名，
        // 因而必须使用延迟解析的分支调用，避免 dedicated server 触碰客户端类。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> xin.vanilla.aotake.client.AotakeClientBootstrap::init);
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

}
