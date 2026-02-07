package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
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
        ClientSide.handle(packet);
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientSide {
        private static void handle(DustbinPageSyncToClient packet) {
            xin.vanilla.aotake.event.ClientEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage());
        }
    }
}
