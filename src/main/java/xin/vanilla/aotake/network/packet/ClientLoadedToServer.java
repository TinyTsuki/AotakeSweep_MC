package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.AotakeUtils;

public class ClientLoadedToServer implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<ClientLoadedToServer> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createResource("client_loaded"));
    public final static StreamCodec<ByteBuf, ClientLoadedToServer> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ClientLoadedToServer decode(@NotNull ByteBuf byteBuf) {
            return new ClientLoadedToServer((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ClientLoadedToServer packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public ClientLoadedToServer() {
    }

    public ClientLoadedToServer(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClientLoadedToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                AotakeSweep.getCustomConfigStatus().add(AotakeUtils.getPlayerUUIDString(player));
                // 同步自定义配置到客户端
                AotakeUtils.sendPacketToPlayer(new CustomConfigSyncToClient(), player);
            }
        });
    }
}
