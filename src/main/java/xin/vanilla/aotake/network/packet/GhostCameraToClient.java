package xin.vanilla.aotake.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;

public record GhostCameraToClient(int entityId, boolean reset) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GhostCameraToClient> ID = new CustomPacketPayload.Type<>(AotakeSweep.createResource("ghost_camera"));
    public static final StreamCodec<FriendlyByteBuf, GhostCameraToClient> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeInt(packet.entityId);
                buf.writeBoolean(packet.reset);
            },
            buf -> new GhostCameraToClient(buf.readInt(), buf.readBoolean())
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
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
