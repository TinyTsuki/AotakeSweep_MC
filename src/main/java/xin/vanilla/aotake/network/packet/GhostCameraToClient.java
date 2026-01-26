package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.AotakePacket;

public class GhostCameraToClient implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createResource("ghost_camera");

    private final int entityId;
    private final boolean reset;

    public GhostCameraToClient(int entityId, boolean reset) {
        this.entityId = entityId;
        this.reset = reset;
    }

    public GhostCameraToClient(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.reset = buf.readBoolean();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeInt(this.entityId);
        buf.writeBoolean(this.reset);
        return buf;
    }

    public static void handle(GhostCameraToClient packet) {
        Minecraft client = Minecraft.getInstance();
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
