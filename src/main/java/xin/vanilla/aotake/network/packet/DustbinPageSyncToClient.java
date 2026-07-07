package xin.vanilla.aotake.network.packet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

public class DustbinPageSyncToClient implements NetworkPacket {
    private final int currentPage;
    private final int totalPage;

    public DustbinPageSyncToClient(int currentPage, int totalPage) {
        this.currentPage = currentPage;
        this.totalPage = totalPage;
    }

    public DustbinPageSyncToClient(BaniraPacketBuffer buf) {
        this.currentPage = buf.readInt();
        this.totalPage = buf.readInt();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    public static void handle(DustbinPageSyncToClient packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
        ctx.markHandled();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(DustbinPageSyncToClient packet) {
            DustbinRender.updateDustbinPage(packet.currentPage, packet.totalPage);
        }
    }
}
