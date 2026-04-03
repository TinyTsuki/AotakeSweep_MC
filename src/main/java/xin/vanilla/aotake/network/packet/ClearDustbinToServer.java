package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import java.util.function.Supplier;

public class ClearDustbinToServer {
    private final boolean all;
    private final boolean cache;

    public ClearDustbinToServer(boolean all, boolean cache) {
        this.all = all;
        this.cache = cache;
    }

    public ClearDustbinToServer(PacketBuffer buf) {
        this.all = buf.readBoolean();
        this.cache = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeBoolean(this.all);
        buf.writeBoolean(this.cache);
    }

    public static void handle(ClearDustbinToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
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
        ctx.get().setPacketHandled(true);
    }
}
