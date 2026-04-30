package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.network.NetworkContext;

public record OpenDustbinToServer(int offset) implements AotakeNetworkPacket {

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset());
    }

    public static void handle(OpenDustbinToServer packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isServerSide()) {
                return;
            }
            ServerPlayer player = ctx.sender();
            if (player == null) {
                return;
            }
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            Integer page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
            int i = page + packet.offset();
            if (i > 0 && i <= AotakeUtils.getDustbinTotalPage()) {
                player.closeContainer();
            }
            AotakeUtils.executeCommand(player, String.format("/%s %s",
                    AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN),
                    i
            ));
        });
    }
}
