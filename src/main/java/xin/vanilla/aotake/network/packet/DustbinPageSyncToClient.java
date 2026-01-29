package xin.vanilla.aotake.network.packet;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.event.ClientGameEventHandler;

import java.util.function.Supplier;

public class DustbinPageSyncToClient {
    private final int currentPage;
    private final int totalPage;

    public DustbinPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }

    public DustbinPageSyncToClient(PacketBuffer buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(DustbinPageSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientGameEventHandler.updateDustbinPage(packet.currentPage, packet.totalPage));
        ctx.get().setPacketHandled(true);
    }
}
