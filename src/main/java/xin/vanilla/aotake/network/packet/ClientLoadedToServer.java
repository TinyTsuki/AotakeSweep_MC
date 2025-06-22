package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

import java.util.function.Supplier;

public class ClientLoadedToServer {

    public ClientLoadedToServer() {
    }

    public ClientLoadedToServer(PacketBuffer buf) {
    }

    public void toBytes(PacketBuffer buf) {
    }

    public static void handle(ClientLoadedToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                AotakeSweep.getCustomConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
                // 同步自定义配置到客户端
                AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
