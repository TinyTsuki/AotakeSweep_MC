package xin.vanilla.aotake;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.EntityFilter;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContext;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContextMenuItem;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.KeyValue;
import xin.vanilla.banira.common.network.ModLoadedPresence;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.EnvironmentUtils;
import xin.vanilla.banira.internal.config.CustomConfig;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


@Accessors(fluent = true)
public class AotakeSweep implements ModInitializer {

    public static final String DEFAULT_COMMAND_PREFIX = "aotake";

    public static final String MODID = "aotake_sweep";

    private static final Logger LOGGER = LogManager.getLogger();

    @Getter
    private static final Map<String, Integer> playerDustbinPage = new ConcurrentHashMap<>();

    private static final Map<String, Integer> playerChunkVaultPage = new ConcurrentHashMap<>();
    private static final Map<String, String> playerChunkVaultId = new ConcurrentHashMap<>();

    public static Map<String, String> getPlayerChunkVaultId() {
        return playerChunkVaultId;
    }

    public static Map<String, Integer> getPlayerChunkVaultPage() {
        return playerChunkVaultPage;
    }

    @Getter
    private static final KeyValue<Long, Long> clientServerTime = new KeyValue<>(0L, 0L);

    @Getter
    private static final KeyValue<Long, Long> sweepTime = new KeyValue<>(0L, 0L);

    @Getter
    private static volatile boolean clientCachedShowSweepResult = true;
    @Getter
    private static volatile boolean clientCachedEnableWarningVoice = true;

    public static void setClientCachedPlayerSweepPrefs(boolean showSweepResult, boolean enableWarningVoice) {
        clientCachedShowSweepResult = showSweepResult;
        clientCachedEnableWarningVoice = enableWarningVoice;
    }

    private static final Set<String> customConfigStatusSet = ConcurrentHashMap.newKeySet();

    public static Set<String> customConfigStatus() {
        return customConfigStatusSet;
    }

    public static KeyValue<MinecraftServer, Boolean> serverInstance() {
        return BaniraCodex.serverInstance();
    }

    public static void reloadCustomConfig() {
        CustomConfig.loadCustomConfig(false);
    }

    public static final Random RANDOM = new Random();

    @Getter
    @Setter
    private static boolean disable = false;

    public static boolean isDisable() {
        return disable;
    }

    @Getter
    private static final EntitySweeper entitySweeper = new EntitySweeper();
    @Getter
    private static final EntityFilter entityFilter = new EntityFilter();

    @Override
    public void onInitialize() {
        NetworkInit.registerPackets();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> entitySweeper.clear());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AotakeCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AotakeNotificationTypes.registerAllOnServer();
            ModLoadedPresence.register(MODID, player -> {
                customConfigStatusSet.add(player.getStringUUID());
                AotakeUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
                CommandUtils.refreshPermission(player);
            });
        });

        ServerEventHandler.register();

        if (EnvironmentUtils.isClient()) {
            ClientProxy.init();
        }
    }

    @Environment(EnvType.CLIENT)
    public static class ClientProxy {
        public static void init() {
            ClientGameEventHandler.register();

            BaniraClientEventHub.ModLifecycle.onClientSetup(() -> {
                ClientModEventHandler.bootstrap();

                ResourceLocation texture = Identifier.id().create("gui/quick_icon.png");
                Component label = AotakeComponent.get().transClient("key.aotake_sweep.categories");
                QuickActionContextMenuItem editClientConfig = new QuickActionContextMenuItem(
                        AotakeComponent.get().transClientAuto("edit_client_config"),
                        ctx -> ConfigEditorScreen.open(ClientConfig.class, ctx.currentScreen())
                );
                Consumer<QuickActionContext> action = ctx -> AotakeUtils.sendPacketToServer(new OpenDustbinToServer(0));
                QuickActionContextMenuItem editCommonConfig = new QuickActionContextMenuItem(
                        AotakeComponent.get().transClientAuto("edit_common_config"),
                        ctx -> ConfigEditorScreen.open(CommonConfig.class, ctx.currentScreen())
                );
                QuickActionContextMenuItem editPlayerConfig = new QuickActionContextMenuItem(
                        AotakeComponent.get().transClientAuto("edit_player_config"),
                        ctx -> Minecraft.getInstance().setScreen(new PlayerConfigScreen(ctx.currentScreen()
                                , AotakeSweep.clientCachedShowSweepResult()
                                , AotakeSweep.clientCachedEnableWarningVoice()))
                );
                QuickActionRegistry.get().registerIcon(MODID + ":quick", texture, label, action, editPlayerConfig, editClientConfig, editCommonConfig);
            });
        }
    }
}
