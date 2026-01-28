package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.event.ClientGameEventHandler;

public record DustbinPageSyncToClient(int currentPage, int totalPage) {

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(DustbinPageSyncToClient packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> ClientGameEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage()));
        ctx.setPacketHandled(true);
    }
}
