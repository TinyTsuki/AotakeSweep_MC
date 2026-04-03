package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements AotakePacket {

public class DustbinPageSyncToClient implements NetworkPacket {
    private final int currentPage;
    private final int totalPage;

    public DustbinPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }
    public static final ResourceLocation ID = AotakeSweep.createIdentifier("dustbin_page_sync");

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
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
            DustbinRender.updateDustbinPage(packet.currentPage, packet.totalPage);
        }
    }
}
