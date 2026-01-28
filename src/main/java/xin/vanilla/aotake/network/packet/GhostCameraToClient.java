package xin.vanilla.aotake.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fmllegacy.network.NetworkEvent;

import java.util.function.Supplier;

public record GhostCameraToClient(int entityId, boolean reset) {

    public GhostCameraToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId());
        buf.writeBoolean(this.reset());
    }

    public static void handle(GhostCameraToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft client = Minecraft.getInstance();
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
        });
    }
}
