package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;

public record ClearDustbinToServer(boolean all, boolean cache) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClearDustbinToServer> ID = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("clear_dustbin"));
    public static final StreamCodec<FriendlyByteBuf, ClearDustbinToServer> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBoolean(packet.all);
                buf.writeBoolean(packet.cache);
            },
            buf -> new ClearDustbinToServer(buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ClearDustbinToServer packet, ServerPlayer player) {
        if (player != null) {
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            int page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
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
    }
}
