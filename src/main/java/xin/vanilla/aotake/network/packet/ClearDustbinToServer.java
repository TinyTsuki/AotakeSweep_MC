package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.network.NetworkContext;

public record ClearDustbinToServer(boolean all, boolean cache) implements AotakeNetworkPacket {

    public ClearDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.all());
        buf.writeBoolean(this.cache());
    }

    public static void handle(ClearDustbinToServer packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isServerSide()) {
                return;
            }
            ServerPlayer player = ctx.sender();
            if (player == null) {
                return;
            }
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            int page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
            if (packet.cache()) {
                AotakeUtils.executeCommand(player, String.format("/%s",
                        AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR)
                ));
            } else {
                AotakeUtils.executeCommand(player, String.format("/%s%s",
                        AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR),
                        packet.all() ? "" : " " + page)
                );
            }
        });
    }
}
