package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

public class ClientLoadedToServer {

    public ClientLoadedToServer() {
    }

    public ClientLoadedToServer(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public static void handle(ClientLoadedToServer packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                AotakeSweep.getCustomConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
                // 同步自定义配置到客户端
                AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
