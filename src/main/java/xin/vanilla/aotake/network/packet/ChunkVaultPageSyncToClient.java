package xin.vanilla.aotake.network.packet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

public class ChunkVaultPageSyncToClient implements NetworkPacket {
    private final int currentPage;
    private final int totalPage;

    public ChunkVaultPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }

    public ChunkVaultPageSyncToClient(BaniraPacketBuffer buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(ChunkVaultPageSyncToClient packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
        ctx.markHandled();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(ChunkVaultPageSyncToClient packet) {
            DustbinRender.updateChunkVaultPage(packet.currentPage, packet.totalPage);
        }
    }
}
