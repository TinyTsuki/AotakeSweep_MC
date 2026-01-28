package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.aotake.event.ClientGameEventHandler;

import java.util.function.Supplier;

public record DustbinPageSyncToClient(int currentPage, int totalPage) {

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(DustbinPageSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientGameEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage()));
        ctx.get().setPacketHandled(true);
    }
}
