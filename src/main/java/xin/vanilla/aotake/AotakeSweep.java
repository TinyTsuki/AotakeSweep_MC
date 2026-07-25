package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.api.BaniraModPresence;
import xin.vanilla.banira.api.event.BaniraEvents;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PacketUtils;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final AtomicBoolean bootstrapped = new AtomicBoolean(false);

    /** 加载器入口安装好 Banira 平台后调用；业务初始化在所有分支保持一致。 */
    public static void bootstrapCommon() {
        if (!bootstrapped.compareAndSet(false, true)) return;
        BaniraConfigs.register(CommonConfig.class, MODID);
        BaniraConfigs.register(ClientConfig.class, MODID);
        BaniraConfigs.onSaved(CommonConfig.class, changedPaths -> {
            boolean commandChanged = changedPaths.stream()
                    .anyMatch(path -> path.startsWith("command.") || path.startsWith("concise."));
            if (commandChanged) {
                AotakeCommand.refreshConfiguredCommands(
                        xin.vanilla.aotake.internal.common.AotakeServerRuntime.currentServer());
            }
        });
        NetworkInit.registerPackets();
        AotakeNotificationTypes.registerAllOnServer();

        BaniraEvents.Server.onStarting(event -> entitySweeper.clear());
        BaniraEvents.Server.onTick(event -> EventHandlerProxy.onServerTick(event.serverAs(net.minecraft.server.MinecraftServer.class)));
        BaniraEvents.World.onTick(event -> EventHandlerProxy.onWorldTick(event.worldAs(net.minecraft.server.level.ServerLevel.class)));
        BaniraEvents.Player.onLoggedIn(event -> EventHandlerProxy.onPlayerLoggedIn(event.playerAs(ServerPlayer.class)));
        BaniraEvents.Player.onLoggedOut(event -> EventHandlerProxy.onPlayerLoggedOut(event.playerAs(ServerPlayer.class)));

        BaniraModPresence.register(MODID, player -> {
            if (!(player instanceof ServerPlayer)) return;
            ServerPlayer serverPlayer = (ServerPlayer) player;
            PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(serverPlayer), serverPlayer);
            CommandUtils.refreshPermission(serverPlayer);
        });
    }

}
