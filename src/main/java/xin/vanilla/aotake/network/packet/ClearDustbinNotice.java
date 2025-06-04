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

public class ClearDustbinNotice implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<ClearDustbinNotice> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createResource("clear_dustbin"));
    public final static StreamCodec<ByteBuf, ClearDustbinNotice> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull ClearDustbinNotice decode(@NotNull ByteBuf byteBuf) {
            return new ClearDustbinNotice((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull ClearDustbinNotice packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    private final boolean all;
    private final boolean cache;

    public ClearDustbinNotice(boolean all, boolean cache) {
        this.all = all;
        this.cache = cache;
    }

    public ClearDustbinNotice(FriendlyByteBuf buf) {
        this.all = buf.readBoolean();
        this.cache = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.all);
        buf.writeBoolean(this.cache);
    }

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearDustbinNotice packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                // 缓存区
                if (packet.cache) {
                    AotakeUtils.executeCommand(player, String.format("/%s"
                            , AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR))
                    );
                }
                // 垃圾箱
                else {
                    AotakeUtils.executeCommand(player, String.format("/%s%s"
                            , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)
                            , packet.all ? "" : " " + page)
                    );
                }
            }
        });
    }
}
