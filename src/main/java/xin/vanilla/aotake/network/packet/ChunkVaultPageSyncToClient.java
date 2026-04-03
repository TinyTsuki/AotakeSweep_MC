package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;

public class ChunkVaultPageSyncToClient implements NetworkPacket {
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

    public static void handle(ChunkVaultPageSyncToClient packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
        ctx.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(ChunkVaultPageSyncToClient packet) {
            DustbinRender.updateChunkVaultPage(packet.currentPage, packet.totalPage);
        }
    }
}
