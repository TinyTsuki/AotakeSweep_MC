package xin.vanilla.aotake;

import xin.vanilla.aotake.internal.server.AotakeSweepRuntime;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.player.ServerPlayerEntity;
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
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.config.BaniraConfig;
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

    public AotakeSweep() {
        // Forge 会在 common setup 前加载 COMMON/CLIENT 配置，必须在构造阶段完成注册。
        BaniraConfig.register(CommonConfig.class, MODID);
        BaniraConfig.register(ClientConfig.class, MODID);
        BaniraConfigs.onSaved(CommonConfig.class, changedPaths -> {
            boolean commandChanged = changedPaths.stream()
                    .anyMatch(path -> path.startsWith("command.") || path.startsWith("concise."));
            if (commandChanged) {
                AotakeCommand.refreshConfiguredCommands(BaniraServer.currentAs(net.minecraft.server.MinecraftServer.class));
            }
        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);

        BaniraEventBus.Server.onStarting(event -> {
            entitySweeper.clear();
            xin.vanilla.aotake.event.EventHandlerProxy.resetCleanupState();
            AotakeRuleRuntime.start(event.serverAs(net.minecraft.server.MinecraftServer.class));
            AotakeSweepRuntime.start(event.serverAs(net.minecraft.server.MinecraftServer.class));
        });
        BaniraEventBus.Server.onStopping(event -> {
            net.minecraft.server.MinecraftServer server = event.serverAs(net.minecraft.server.MinecraftServer.class);
            try {
                try { AotakeSweepRuntime.stop(); }
                finally { if (server != null) server.getAllLevels().forEach(EntitySweeper::flushPendingRemovals); }
            } finally {
                try { entitySweeper.clear(); }
                finally {
                    xin.vanilla.aotake.event.EventHandlerProxy.resetCleanupState();
                    AotakeRuleRuntime.stop();
                }
            }
        });
        AotakeNetworkSmokeServerRunner.register();
        xin.vanilla.aotake.internal.server.dev.AotakeRuleNetworkSmokeServerRunner.register();
        xin.vanilla.aotake.internal.server.dev.AotakeSweepPolicySmokeServerRunner.register();
        ForgeAotakeGameEventAdapter.register();

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

}
