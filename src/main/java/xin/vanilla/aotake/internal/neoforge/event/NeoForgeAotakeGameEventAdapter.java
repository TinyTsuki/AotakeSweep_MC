package xin.vanilla.aotake.internal.neoforge.event;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.platform.BaniraConfigHandle;

/**
 * 将 NeoForge 专属事件转换到 Aotake 的公共业务处理器。
 */
public final class NeoForgeAotakeGameEventAdapter {
    private static boolean registered;

    private NeoForgeAotakeGameEventAdapter() {
    }

    public static synchronized void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }
        registered = true;

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                AotakeCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) ->
                EventHandlerProxy.onServerTick(event));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre event) ->
                EventHandlerProxy.onWorldTick(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) ->
                EventHandlerProxy.onPlayerUseItem(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) ->
                EventHandlerProxy.onRightBlock(event));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) ->
                EventHandlerProxy.onRightEntity(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) ->
                EventHandlerProxy.onPlayerLoggedIn(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                EventHandlerProxy.onPlayerLoggedOut(event));
        // Banira 的泛型玩家事件不覆盖拉弓，需保留 NeoForge 专属桥接。
        NeoForge.EVENT_BUS.addListener((ArrowNockEvent event) -> EventHandlerProxy.onPlayerUseItem(event));
        NeoForge.EVENT_BUS.addListener(ChunkVaultSession::onContainerClose);
        modEventBus.addListener(NeoForgeAotakeGameEventAdapter::onConfigReload);
    }

    private static void onConfigReload(ModConfigEvent event) {
        try {
            ModConfig config = event.getConfig();
            BaniraConfigHandle common = BaniraConfigs.handle(CommonConfig.class);
            if (common != null && config.getFileName().contains(common.getConfigName()) && BaniraServer.isRunning()) {
                AotakeUtils.clearEntityFilterCaches();
            }
        } catch (Exception ignored) {
            // 配置事件可能早于服务端状态建立，此时无需刷新运行时缓存。
        }
    }
}
