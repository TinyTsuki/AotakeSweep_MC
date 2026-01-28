package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;

public record OpenDustbinToServer(int offset) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenDustbinToServer> ID = new CustomPacketPayload.Type<>(AotakeSweep.createIdentifier("open_dustbin"));
    public static final StreamCodec<FriendlyByteBuf, OpenDustbinToServer> CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeInt(packet.offset),
            buf -> new OpenDustbinToServer(buf.readInt())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(OpenDustbinToServer packet, ServerPlayer player) {
        if (player != null) {
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            Integer page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
            int i = page + packet.offset();
            if (i > 0 && i <= AotakeUtils.getDustbinTotalPage()) {
                player.closeContainer();
            }
            AotakeUtils.executeCommand(player, String.format("/%s %s"
                    , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN)
                    , i
            ));
        }
    }
}
