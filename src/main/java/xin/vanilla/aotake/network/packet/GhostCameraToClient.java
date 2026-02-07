package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.AotakePacket;

public record GhostCameraToClient(int entityId, boolean reset) implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createIdentifier("ghost_camera");

    public GhostCameraToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
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
        ClientSide.handle(packet);
    }

    @Environment(EnvType.CLIENT)
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
