package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.SweepTimeSyncToClient;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContext;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContextMenuItem;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ForgeConfigAdapter;
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

    public AotakeSweep() {
        // 注册网络通道
        NetworkInit.registerPackets();

        // 注册配置
        ForgeConfigAdapter.register(CommonConfig.class, MODID);
        ForgeConfigAdapter.register(ClientConfig.class, MODID);

        BaniraEventBus.Server.onStarting(server -> entitySweeper.clear());
        BaniraEventBus.Commands.onRegister(event -> AotakeCommand.register(event.getDispatcher()));

        BaniraEventBus.ModLifecycle.onCommonSetup(event -> {
            ModLoadedPresence.register(MODID, player -> {
                // 同步清理时间到客户端
                PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepTimeSyncToClient(), player);
                // 刷新权限信息
                CommandUtils.refreshPermission(player);
            });
        });

        BaniraEventBus.Server.onTick(EventHandlerProxy::onServerTick);
        BaniraEventBus.WorldEvents.onTick(EventHandlerProxy::onWorldTick);
        BaniraEventBus.Player.onClone(EventHandlerProxy::onPlayerCloned);
        BaniraEventBus.Player.onPlayerEvent(event -> {
            if (event instanceof PlayerEvent.Clone) return;
            EventHandlerProxy.onPlayerUseItem(event);
        });
        BaniraEventBus.Interaction.onRightClickItem(EventHandlerProxy::onPlayerUseItem);
        BaniraEventBus.Interaction.onRightClickBlock(EventHandlerProxy::onRightBlock);
        BaniraEventBus.Interaction.onEntityInteractSpecific(EventHandlerProxy::onRightEntity);
        BaniraEventBus.Player.onLoggedIn(player -> EventHandlerProxy.onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player)));
        BaniraEventBus.Player.onLoggedOut(player -> EventHandlerProxy.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player)));

        // 注册配置文件重载事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        if (EnvironmentUtils.isClient()) {
            ClientProxy.init();
        }
    }

    public void onConfigReload(ModConfig.ModConfigEvent event) {
        try {
            ModConfig cfg = event.getConfig();
            ConfigHolder commonHolder = ForgeConfigAdapter.getHolder(CommonConfig.class);
            if (commonHolder != null && cfg.getSpec() == commonHolder.getSpec() && BaniraCodex.serverInstance().val()) {
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
                Consumer<QuickActionContext> action = ctx -> PacketUtils.sendPacketToServer(NetworkInit.INSTANCE, new OpenDustbinToServer(0));
                QuickActionContextMenuItem editCommonConfig = new QuickActionContextMenuItem(AotakeComponent.get().transClientAuto("edit_common_config"), ctx ->
                        ConfigEditorScreen.open(CommonConfig.get().holder(), ctx.currentScreen())
                );
                QuickActionContextMenuItem editPlayerConfig = new QuickActionContextMenuItem(AotakeComponent.get().transClientAuto("edit_player_config"), ctx -> {
                    // TODO 打开玩家配置界面
                });
                QuickActionRegistry.get().registerIcon(MODID + ":quick", texture, label, action, editPlayerConfig, editClientConfig, editCommonConfig);
            });
        }
    }

}
