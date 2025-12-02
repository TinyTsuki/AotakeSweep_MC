package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.AotakePacket;
import xin.vanilla.aotake.util.AotakeUtils;

public class OpenDustbinToServer implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createResource("open_dustbin");

    private final int offset;

    public OpenDustbinToServer(int offset) {
        this.offset = offset;
    }

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this.offset = buf.readInt();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeInt(this.offset);
        return buf;
    }

    public static void handle(OpenDustbinToServer packet, ServerPlayer player) {
        if (player != null) {
            String playerUUID = AotakeUtils.getPlayerUUIDString(player);
            Integer page = AotakeSweep.playerDustbinPage().getOrDefault(playerUUID, 1);
            int i = page + packet.offset;
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
