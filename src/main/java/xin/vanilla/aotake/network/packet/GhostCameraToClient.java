package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.Entity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class GhostCameraToClient {

    private final int entityId;
    private final boolean reset;


    public GhostCameraToClient(int entityId, boolean reset) {
        this.entityId = entityId;
        this.reset = reset;
    }

    public GhostCameraToClient(PacketBuffer buf) {
        this.entityId = buf.readInt();
        this.reset = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.reset);
    }

    public static void handle(GhostCameraToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientSide.handle(packet));
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientSide {
        private static void handle(GhostCameraToClient packet) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client.player == null) return;
            if (packet.reset) {
                client.setCameraEntity(client.player);
                return;
            }
            if (client.level == null) return;
            Entity entity = client.level.getEntity(packet.entityId);
            if (entity != null) {
                client.setCameraEntity(entity);
            }
        }
    }
}
