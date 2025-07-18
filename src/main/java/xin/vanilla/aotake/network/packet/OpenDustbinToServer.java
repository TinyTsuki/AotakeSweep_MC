package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.util.AotakeUtils;

import java.util.function.Supplier;

public class OpenDustbinToServer {
    private final int offset;

    public OpenDustbinToServer(int offset) {
        this.offset = offset;
    }

    public OpenDustbinToServer(PacketBuffer buf) {
        this.offset = buf.readInt();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.offset);
    }

    public static void handle(OpenDustbinToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player != null) {
                String playerUUID = AotakeUtils.getPlayerUUIDString(player);
                Integer page = AotakeSweep.getPlayerDustbinPage().getOrDefault(playerUUID, 1);
                int i = page + packet.offset;
                if (i > 0 && i <= CommonConfig.DUSTBIN_PAGE_LIMIT.get()) {
                    player.closeContainer();
                }
                INamedContainerProvider trashContainer = WorldTrashData.getTrashContainer(player, i);
                if (trashContainer != null) {
                    int result = player.openMenu(trashContainer).orElse(0);
                    if (result > 0) AotakeSweep.getPlayerDustbinPage().put(playerUUID, i);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
