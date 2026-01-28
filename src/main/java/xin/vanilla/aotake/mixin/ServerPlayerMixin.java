package xin.vanilla.aotake.mixin;

import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xin.vanilla.aotake.util.PlayerLanguageManager;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(
            method = "updateOptions",
            at = @At("TAIL")
    )
    private void aotake$afterUpdateOptions(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        PlayerLanguageManager.set(player, packet.getLanguage());
    }
}
