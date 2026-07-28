package xin.vanilla.aotake.internal.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.command.AotakeCommand;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.banira.common.util.BaniraEventBus;

/**
 * Fabric 1.19.2 适配入口，只负责把加载器回调转换为 Aotake 的稳定业务方法。
 */
public final class FabricAotakeEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        AotakeSweep.bootstrapCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AotakeCommand.register(dispatcher));
        UseItemCallback.EVENT.register(EventHandlerProxy::onPlayerUseItem);
        UseBlockCallback.EVENT.register(EventHandlerProxy::onRightBlock);
        UseEntityCallback.EVENT.register(EventHandlerProxy::onRightEntity);
        ServerTickEvents.START_WORLD_TICK.register(EventHandlerProxy::onWorldTick);
        BaniraEventBus.PlayerEvents.onLoggedOut(event -> {
            ServerPlayer player = event.playerAs(ServerPlayer.class);
            if (player != null) ChunkVaultSession.onPlayerCloseContainer(player);
        });
    }
}
