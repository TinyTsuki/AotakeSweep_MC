package xin.vanilla.aotake.network.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.screen.DustbinRender;

import java.util.function.Supplier;

public class ChunkVaultPageSyncToClient {
    private final int currentPage;
    private final int totalPage;

    public ChunkVaultPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }

    public ChunkVaultPageSyncToClient(PacketBuffer buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(ChunkVaultPageSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSide.handle(packet));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(ChunkVaultPageSyncToClient packet) {
            DustbinRender.updateChunkVaultPage(packet.currentPage, packet.totalPage);
        }
    }
}
