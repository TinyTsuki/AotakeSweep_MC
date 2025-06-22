package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.util.AotakeUtils;

public class OpenDustbinToServer implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<OpenDustbinToServer> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createResource("open_dustbin"));
    public final static StreamCodec<ByteBuf, OpenDustbinToServer> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull OpenDustbinToServer decode(@NotNull ByteBuf byteBuf) {
            return new OpenDustbinToServer((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull OpenDustbinToServer packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

    private final int offset;

    public OpenDustbinToServer(int offset) {
        this.offset = offset;
    }

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDustbinToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                int i = page + packet.offset;
                if (i > 0 && i <= CommonConfig.DUSTBIN_PAGE_LIMIT.get()) {
                    player.closeContainer();
                }
                MenuProvider trashContainer = WorldTrashData.getTrashContainer(player, i);
                if (trashContainer != null) {
                    int result = player.openMenu(trashContainer).orElse(0);
                    if (result > 0) AotakeSweep.getPlayerDustbinPage().put(playerUUID, i);
                }
            }
        });
    }
}
