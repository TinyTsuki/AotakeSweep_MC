package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.common.network.NetworkContext;

public class ChunkVaultPageSyncToClient implements AotakeNetworkPacket {
    private final int currentPage;
    private final int totalPage;

    public ChunkVaultPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }

    public ChunkVaultPageSyncToClient(FriendlyByteBuf buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public int currentPage() {
        return currentPage;
    }

    public int totalPage() {
        return totalPage;
    }

    public static void handle(ChunkVaultPageSyncToClient packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isClientSide()) {
                return;
            }
            ClientSide.handle(packet);
        });
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientSide {
        private static void handle(ChunkVaultPageSyncToClient packet) {
            DustbinRender.updateChunkVaultPage(packet.currentPage, packet.totalPage);
        }
    }
}
