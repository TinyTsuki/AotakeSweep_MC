package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;

public class ClearDustbinNotice {
    private final boolean all;
    private final boolean cache;

    public ClearDustbinNotice(boolean all, boolean cache) {
        this.all = all;
        this.cache = cache;
    }

    public ClearDustbinNotice(FriendlyByteBuf buf) {
        this.all = buf.readBoolean();
        this.cache = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.all);
        buf.writeBoolean(this.cache);
    }

    public static void handle(ClearDustbinNotice packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                int page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                // 缓存区
                if (packet.cache) {
                    AotakeUtils.executeCommand(player, String.format("/%s"
                            , AotakeUtils.getCommand(EnumCommandType.CACHE_CLEAR))
                    );
                }
                // 垃圾箱
                else {
                    AotakeUtils.executeCommand(player, String.format("/%s%s"
                            , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_CLEAR)
                            , packet.all ? "" : " " + page)
                    );
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
