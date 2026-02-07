package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fmllegacy.network.NetworkEvent;

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
        ctx.get().enqueueWork(() -> ClientSide.handle(packet));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(DustbinPageSyncToClient packet) {
            xin.vanilla.aotake.event.ClientGameEventHandler.updateDustbinPage(packet.currentPage, packet.totalPage);
        }
    }
}
