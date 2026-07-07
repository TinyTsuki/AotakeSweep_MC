package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContext;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContextMenuItem;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.network.ModLoadedPresence;
import xin.vanilla.banira.common.util.BaniraEventBus;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.EnvironmentUtils;
import xin.vanilla.banira.common.util.PacketUtils;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
        // Banira 平台在 common setup 阶段完成安装，配置与网络注册需要延后到那里执行。
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);

        BaniraEventBus.Server.onStarting(server -> entitySweeper.clear());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AotakeCommand.register(event.getDispatcher()));

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> EventHandlerProxy.onServerTick(event));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.WorldTickEvent event) -> EventHandlerProxy.onWorldTick(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> EventHandlerProxy.onPlayerCloned(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> EventHandlerProxy.onPlayerUseItem(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> EventHandlerProxy.onRightBlock(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> EventHandlerProxy.onRightEntity(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> EventHandlerProxy.onPlayerLoggedIn(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> EventHandlerProxy.onPlayerLoggedOut(event));

        // 注册配置文件重载事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.addListener(ChunkVaultSession::onContainerClose);

        if (EnvironmentUtils.isClient()) {
            ClientProxy.init();
        }
    }

    public void onCommonSetup(FMLCommonSetupEvent event) {
        BaniraConfig.register(CommonConfig.class, MODID);
        BaniraConfig.register(ClientConfig.class, MODID);
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
            if (commonHolder != null && cfg.getFileName().contains(commonHolder.getConfigName()) && BaniraCodex.serverInstance().val()) {
                entityFilter.clear();
            }
        } catch (Exception ignored) {
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ClientProxy {
        public static void init() {
            ClientGameEventHandler.register();

            BaniraClientEventHub.ModLifecycle.onClientSetup(event -> {
                ClientModEventHandler.bootstrap();

                ResourceLocation texture = Identifier.id().create("gui/quick_icon.png");
                Component label = AotakeComponent.get().transClient("key.aotake_sweep.categories");
                QuickActionContextMenuItem editClientConfig = new QuickActionContextMenuItem(AotakeComponent.get().transClientAuto("edit_client_config"), ctx ->
                        ConfigEditorScreen.open(ClientConfig.get().holder(), ctx.currentScreen())
                );
                Consumer<QuickActionContext> action = ctx -> PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
                QuickActionContextMenuItem editCommonConfig = new QuickActionContextMenuItem(AotakeComponent.get().transClientAuto("edit_common_config"), ctx ->
                        ConfigEditorScreen.open(CommonConfig.get().holder(), ctx.currentScreen())
                );
                QuickActionContextMenuItem editPlayerConfig = new QuickActionContextMenuItem(AotakeComponent.get().transClientAuto("edit_player_config"), ctx ->
                        Minecraft.getInstance().setScreen(new PlayerConfigScreen(ctx.currentScreen()
                                , AotakeSweep.isClientCachedShowSweepResult()
                                , AotakeSweep.isClientCachedEnableWarningVoice()))
                );
                QuickActionRegistry.get().registerIcon(MODID + ":quick", texture, label, action, editPlayerConfig, editClientConfig, editCommonConfig);
            });
        }
    }

}
