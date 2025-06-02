package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.util.AotakeUtils;

import java.util.function.Supplier;

public class OpenDustbinNotice {
    private final int offset;

    public OpenDustbinNotice(int offset) {
        this.offset = offset;
    }

    public OpenDustbinNotice(FriendlyByteBuf buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(OpenDustbinNotice packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                if (packet.offset == 0) page = 1;
                int result = player.openMenu(WorldTrashData.getTrashContainer(player, page + packet.offset)).orElse(0);
                if (result > 0) AotakeSweep.getPlayerDustbinPage().put(playerUUID, page + packet.offset);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
