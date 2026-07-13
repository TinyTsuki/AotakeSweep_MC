package xin.vanilla.aotake.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.data.world.ChunkVaultSession;

/** Fabric 没有通用容器关闭事件，在原版关闭入口持久化区块暂存箱会话。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "closeContainer", at = @At("HEAD"))
    private void aotake$onCloseContainer(CallbackInfo ci) {
        ChunkVaultSession.onPlayerCloseContainer((ServerPlayer) (Object) this);
    }
}
