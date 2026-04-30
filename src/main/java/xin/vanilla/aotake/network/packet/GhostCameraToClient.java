package xin.vanilla.aotake.network.packet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.banira.common.network.NetworkContext;

public record GhostCameraToClient(int entityId, boolean reset) implements AotakeNetworkPacket {

    public GhostCameraToClient(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId());
        buf.writeBoolean(this.reset());
    }

    public static void handle(GhostCameraToClient packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isClientSide()) {
                return;
            }
            ClientSide.handle(packet);
        });
    }

    @Environment(EnvType.CLIENT)
    private static final class ClientSide {
        private static void handle(GhostCameraToClient packet) {
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
        }
    }
}
