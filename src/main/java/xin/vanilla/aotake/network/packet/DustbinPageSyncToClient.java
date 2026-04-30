package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import xin.vanilla.aotake.event.ClientEventHandler;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.banira.common.network.NetworkContext;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements AotakeNetworkPacket {

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.currentPage());
        buf.writeInt(this.totalPage());
    }

    public static void handle(DustbinPageSyncToClient packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isClientSide()) {
                return;
            }
            ClientSide.handle(packet);
        });
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientSide {
        private static void handle(DustbinPageSyncToClient packet) {
            ClientEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage());
        }
    }
}
