package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.util.CommandUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import java.util.function.Supplier;

public record OpenDustbinToServer(int offset)implements NetworkPacket {

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset());
    }

    public static void handle(OpenDustbinToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String playerUUID = PlayerUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                int i = page + packet.offset();
                if (i > 0 && i <= AotakeUtils.getDustbinTotalPage()) {
                    player.closeContainer();
                }
                CommandUtils.executeCommand(player, String.format("/%s %s"
                        , AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN)
                        , i
                ));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
