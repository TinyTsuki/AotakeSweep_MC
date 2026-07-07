package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

public class ClearDustbinToServer implements NetworkPacket {
    private final boolean all;
    private final boolean cache;

    public ClearDustbinToServer(boolean all, boolean cache) {
        this.all = all;
        this.cache = cache;
    }

    public ClearDustbinToServer(BaniraPacketBuffer buf) {
        this.all = buf.readBoolean();
        this.cache = buf.readBoolean();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeBoolean(this.all);
        buf.writeBoolean(this.cache);
    }

    public static void handle(ClearDustbinToServer packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayerEntity player = ctx.senderAs(ServerPlayerEntity.class);
            if (player != null) {
                String playerUUID = PlayerUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                // 缓存区
                if (packet.cache) {
                    CommandUtils.executeCommand(player, String.format("/%s"
                            , AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR))
                    );
                }
                // 垃圾箱
                else {
                    CommandUtils.executeCommand(player, String.format("/%s%s"
                            , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)
                            , packet.all ? "" : " " + page)
                    );
                }
            }
        });
        ctx.markHandled();
    }
}
