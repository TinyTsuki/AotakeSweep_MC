package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;

public record ClearDustbinToServer(boolean all, boolean cache) implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<ClearDustbinToServer> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("clear_dustbin"));
    public final static StreamCodec<ByteBuf, ClearDustbinToServer> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ClearDustbinToServer decode(@NotNull ByteBuf byteBuf) {
            return new ClearDustbinToServer((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ClearDustbinToServer packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    public ClearDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.all());
        buf.writeBoolean(this.cache());
    }

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearDustbinToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                // 缓存区
                if (packet.cache()) {
                    AotakeUtils.executeCommand(player, String.format("/%s"
                            , AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR))
                    );
                }
                // 垃圾箱
                else {
                    AotakeUtils.executeCommand(player, String.format("/%s%s"
                            , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)
                            , packet.all() ? "" : " " + page)
                    );
                }
            }
        });
    }
}
