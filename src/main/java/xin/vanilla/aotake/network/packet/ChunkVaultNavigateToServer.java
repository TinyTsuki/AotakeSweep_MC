package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

public class ChunkVaultNavigateToServer implements NetworkPacket {
    private final int offset;

    public ChunkVaultNavigateToServer(int offset) {
        this.offset = offset;
    }

    public ChunkVaultNavigateToServer(BaniraPacketBuffer buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(ChunkVaultNavigateToServer packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.senderAs(ServerPlayerEntity.class);
            if (player == null) return;
            ChunkVaultSession.navigateOrReload(player, packet.offset);
        });
        ctx.markHandled();
    }
}
