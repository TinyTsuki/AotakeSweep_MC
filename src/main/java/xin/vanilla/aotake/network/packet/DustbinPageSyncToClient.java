package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.event.ClientEventHandler;
import xin.vanilla.aotake.network.AotakePacket;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements AotakePacket {

    public static final ResourceLocation ID = AotakeSweep.createIdentifier("dustbin_page_sync");

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
        return buf;
    }

    public static void handle(DustbinPageSyncToClient packet) {
        ClientEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage());
    }
}
