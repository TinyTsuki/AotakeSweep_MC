package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.util.AotakeUtils;

public class OpenDustbinToServer {
    private final int offset;

    public OpenDustbinToServer(int offset) {
        this.offset = offset;
    }

    public OpenDustbinToServer(FriendlyByteBuf buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(OpenDustbinToServer packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                int i = page + packet.offset;
                if (i > 0 && i <= CommonConfig.DUSTBIN_PAGE_LIMIT.get()) {
                    player.closeContainer();
                }
                MenuProvider trashContainer = WorldTrashData.getTrashContainer(player, i);
                if (trashContainer != null) {
                    int result = player.openMenu(trashContainer).orElse(0);
                    if (result > 0) AotakeSweep.getPlayerDustbinPage().put(playerUUID, i);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
