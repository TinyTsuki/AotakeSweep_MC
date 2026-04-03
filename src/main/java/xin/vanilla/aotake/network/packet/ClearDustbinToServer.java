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

public record ClearDustbinToServer(boolean all, boolean cache) implements NetworkPacket {
    public final static CustomPacketPayload.Type<ClearDustbinToServer> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("clear_dustbin"));
    public final static StreamCodec<RegistryFriendlyByteBuf, ClearDustbinToServer> STREAM_CODEC = BaniraStreamCodecs.registryBuf(ClearDustbinToServer::toBytes, ClearDustbinToServer::new);

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
                String playerUUID = PlayerUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                // 缓存区
                if (packet.cache()) {
                    CommandUtils.executeCommand(player, String.format("/%s"
                            , AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR))
                    );
                }
                // 垃圾箱
                else {
                    CommandUtils.executeCommand(player, String.format("/%s%s"
                            , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)
                            , packet.all() ? "" : " " + page)
                    );
                }
            }
        });
    }
}
