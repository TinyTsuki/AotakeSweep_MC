package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

public record ClearDustbinToServer(boolean all, boolean cache) implements NetworkPacket{

    public ClearDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.all());
        buf.writeBoolean(this.cache());
    }

    public static void handle(ClearDustbinToServer packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
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
        ctx.setPacketHandled(true);
    }
}
