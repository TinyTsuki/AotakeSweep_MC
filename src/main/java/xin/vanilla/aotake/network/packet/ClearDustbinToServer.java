package xin.vanilla.aotake.network.packet;

import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

public record ClearDustbinToServer(boolean all, boolean cache) implements NetworkPacket {

    public ClearDustbinToServer(BaniraPacketBuffer buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeBoolean(this.all());
        buf.writeBoolean(this.cache());
    }

    public static void handle(ClearDustbinToServer packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.senderAs(ServerPlayer.class);
            if (player != null) {
                String playerUUID = PlayerUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                CommandUtils.executeCommand(player, buildCommand(packet.all(), packet.cache(), page,
                        AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR),
                        AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)));
            }
        });
        ctx.markHandled();
    }

    static String buildCommand(boolean all, boolean cache, int page,
                               String cacheCommand, String dustbinCommand) {
        if (cache) {
            return cacheCommand;
        }
        return all ? dustbinCommand : dustbinCommand + " " + page;
    }
}
