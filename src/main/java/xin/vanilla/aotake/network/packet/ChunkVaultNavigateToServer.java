package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.data.world.ChunkVaultSession;

import java.util.function.Supplier;

public class ChunkVaultNavigateToServer {
    private final int offset;

    public ChunkVaultNavigateToServer(int offset) {
        this.offset = offset;
    }

    public ChunkVaultNavigateToServer(PacketBuffer buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(ChunkVaultNavigateToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) return;
            ChunkVaultSession.navigateOrReload(player, packet.offset);
        });
        ctx.get().setPacketHandled(true);
    }
}
