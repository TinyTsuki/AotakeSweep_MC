package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.screen.DustbinRender;
import xin.vanilla.banira.internal.network.BaniraStreamCodecs;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements NetworkPacket {
    public final static CustomPacketPayload.Type<DustbinPageSyncToClient> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("dustbin_page_sync"));
    public final static StreamCodec<RegistryFriendlyByteBuf, DustbinPageSyncToClient> STREAM_CODEC = BaniraStreamCodecs.registryBuf(DustbinPageSyncToClient::toBytes, DustbinPageSyncToClient::new);

    public DustbinPageSyncToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.currentPage);
        buf.writeInt(this.totalPage);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DustbinPageSyncToClient packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(DustbinPageSyncToClient packet) {
            DustbinRender.updateDustbinPage(packet.currentPage(), packet.totalPage());
        }
    }
}
