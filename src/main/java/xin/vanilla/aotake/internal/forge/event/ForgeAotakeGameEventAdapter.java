package xin.vanilla.aotake.internal.forge.event;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.platform.BaniraConfigHandle;

/**
 * 将 Forge 原生事件转换到 Aotake 的公共业务处理器。
 */
public final class ForgeAotakeGameEventAdapter {
    private static boolean registered;

    private ForgeAotakeGameEventAdapter() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                AotakeCommand.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) ->
                EventHandlerProxy.onServerTick(event));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.WorldTickEvent event) ->
                EventHandlerProxy.onWorldTick(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) ->
                EventHandlerProxy.onPlayerUseItem(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) ->
                EventHandlerProxy.onRightBlock(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) ->
                EventHandlerProxy.onRightEntity(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) ->
                EventHandlerProxy.onPlayerLoggedIn(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                EventHandlerProxy.onPlayerLoggedOut(event));
        MinecraftForge.EVENT_BUS.addListener(ChunkVaultSession::onContainerClose);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeAotakeGameEventAdapter::onConfigReload);
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
