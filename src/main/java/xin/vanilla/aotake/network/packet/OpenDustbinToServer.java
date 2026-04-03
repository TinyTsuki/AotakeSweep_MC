package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;
import xin.vanilla.banira.internal.network.BaniraStreamCodecs;

public record OpenDustbinToServer(int offset) implements NetworkPacket {
    public final static CustomPacketPayload.Type<OpenDustbinToServer> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("open_dustbin"));
    public final static StreamCodec<RegistryFriendlyByteBuf, OpenDustbinToServer> STREAM_CODEC = BaniraStreamCodecs.registryBuf(OpenDustbinToServer::toBytes, OpenDustbinToServer::new);

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDustbinToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                String playerUUID = PlayerUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                int i = page + packet.offset();
                if (i > 0 && i <= AotakeUtils.getDustbinTotalPage()) {
                    player.closeContainer();
                }
                CommandUtils.executeCommand(player, String.format("/%s %s"
                        , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN)
                        , i
                ));
            }
        });
    }
}
