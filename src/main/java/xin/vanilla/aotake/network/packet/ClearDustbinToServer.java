package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.util.AotakeUtils;

public class ClearDustbinToServer implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createResource("clear_dustbin");

    private final boolean all;
    private final boolean cache;

    public ClearDustbinToServer(boolean all, boolean cache) {
        this.all = all;
        this.cache = cache;
    }

    public ClearDustbinToServer(FriendlyByteBuf buf) {
        this.all = buf.readBoolean();
        this.cache = buf.readBoolean();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeBoolean(this.all);
        buf.writeBoolean(this.cache);
        return buf;
    }

    public static void handle(ClearDustbinToServer packet, ServerPlayer player) {
        if (player != null) {
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            int page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
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
    }
}
