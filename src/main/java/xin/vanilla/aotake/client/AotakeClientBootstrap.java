package xin.vanilla.aotake.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.ClientGameEventHandler;
import xin.vanilla.aotake.event.ClientModEventHandler;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;
import xin.vanilla.aotake.network.packet.OpenDustbinToServer;
import xin.vanilla.aotake.screen.PlayerConfigScreen;
import xin.vanilla.banira.client.event.BaniraClientEventHub;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContext;
import xin.vanilla.banira.client.gui.quickaction.QuickActionContextMenuItem;
import xin.vanilla.banira.client.gui.quickaction.QuickActionRegistry;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.util.PacketUtils;

import java.util.function.Consumer;

/**
 * 客户端初始化入口；只允许通过 DistExecutor 在 CLIENT 端加载。
 */
public final class AotakeClientBootstrap {
    private AotakeClientBootstrap() {
    }

    public static void init() {
        ClientGameEventHandler.register();

        BaniraClientEventHub.ModLifecycle.onClientSetup(event -> {
            ClientModEventHandler.bootstrap();

            ResourceLocation texture = Identifier.id().create("gui/quick_icon.png");
            Component label = AotakeComponent.get().transClient("key.aotake_sweep.categories");
            QuickActionContextMenuItem editClientConfig = new QuickActionContextMenuItem(
                    AotakeComponent.get().transClientAuto("edit_client_config"),
                    ctx -> ConfigEditorScreen.open(ClientConfig.get().holder(), ctx.currentScreen())
            );
            Consumer<QuickActionContext> action = ctx -> PacketUtils.sendPacketToServer(new OpenDustbinToServer(0));
            QuickActionContextMenuItem editCommonConfig = new QuickActionContextMenuItem(
                    AotakeComponent.get().transClientAuto("edit_common_config"),
                    ctx -> ConfigEditorScreen.open(CommonConfig.get().holder(), ctx.currentScreen())
            );
            QuickActionContextMenuItem editPlayerConfig = new QuickActionContextMenuItem(
                    AotakeComponent.get().transClientAuto("edit_player_config"),
                    ctx -> Minecraft.getInstance().setScreen(new PlayerConfigScreen(ctx.currentScreen(),
                            AotakeSweep.isClientCachedShowSweepResult(),
                            AotakeSweep.isClientCachedEnableWarningVoice()))
            );
            QuickActionRegistry.get().registerIcon(AotakeSweep.MODID + ":quick", texture, label, action,
                    editPlayerConfig, editClientConfig, editCommonConfig);
            AotakeUiSmokeRunner.register();
        });
    }
}
