package xin.vanilla.aotake.network.packet;

import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

public record GhostCameraToClient(int entityId, boolean reset) implements NetworkPacket{

    public GhostCameraToClient(BaniraPacketBuffer buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeInt(this.entityId());
        buf.writeBoolean(this.reset());
    }

    public static void handle(GhostCameraToClient packet, BaniraNetworkContext ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
        ctx.markHandled();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(GhostCameraToClient packet) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player == null) return;
            if (packet.reset()) {
                client.setCameraEntity(client.player);
                return;
            }
            if (client.level == null) return;
            Entity entity = client.level.getEntity(packet.entityId());
            if (entity != null) {
                client.setCameraEntity(entity);
            }
        }
    }
}
