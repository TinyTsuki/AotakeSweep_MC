package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.event.ClientGameEventHandler;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<DustbinPageSyncToClient> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("dustbin_page_sync"));
    public final static StreamCodec<ByteBuf, DustbinPageSyncToClient> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull DustbinPageSyncToClient decode(@NotNull ByteBuf byteBuf) {
            return new DustbinPageSyncToClient((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull DustbinPageSyncToClient packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

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
        ctx.enqueueWork(() -> ClientGameEventHandler.updateDustbinPage(packet.currentPage(), packet.totalPage()));
    }
}
