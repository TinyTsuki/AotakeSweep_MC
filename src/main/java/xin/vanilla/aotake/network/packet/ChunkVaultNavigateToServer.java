package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.network.NetworkPacket;

public class ChunkVaultNavigateToServer implements NetworkPacket {
    private final int offset;

    public ChunkVaultNavigateToServer(int offset) {
        this.offset = offset;
    }

    public ChunkVaultNavigateToServer(FriendlyByteBuf buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(ChunkVaultNavigateToServer packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ChunkVaultSession.navigateOrReload(player, packet.offset);
        });
        ctx.setPacketHandled(true);
    }
}
