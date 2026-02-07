package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.network.CustomPayloadEvent;

public record GhostCameraToClient(int entityId, boolean reset) {

    public GhostCameraToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId());
        buf.writeBoolean(this.reset());
    }

    public static void handle(GhostCameraToClient packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> ClientSide.handle(packet));
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
