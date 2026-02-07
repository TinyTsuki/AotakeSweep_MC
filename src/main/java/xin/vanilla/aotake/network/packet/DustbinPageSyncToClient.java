package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;

public record DustbinPageSyncToClient(int currentPage, int totalPage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DustbinPageSyncToClient> ID = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("dustbin_page_sync"));
    public static final StreamCodec<FriendlyByteBuf, DustbinPageSyncToClient> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.currentPage());
                buf.writeInt(packet.totalPage());
            },
            buf -> new DustbinPageSyncToClient(buf.readInt(), buf.readInt())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
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
