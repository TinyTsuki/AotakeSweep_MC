package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.banira.common.network.NetworkContext;

public class ChunkVaultNavigateToServer implements AotakeNetworkPacket {
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

    public int offset() {
        return offset;
    }

    public static void handle(ChunkVaultNavigateToServer packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isServerSide()) {
                return;
            }
            ServerPlayer player = ctx.sender();
            if (player == null) {
                return;
            }
            ChunkVaultSession.navigateOrReload(player, packet.offset);
        });
    }
}
