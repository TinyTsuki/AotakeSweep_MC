package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.data.world.ChunkVaultSession;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.internal.network.BaniraStreamCodecs;

public record ChunkVaultNavigateToServer(int offset) implements NetworkPacket {
    public final static CustomPacketPayload.Type<ChunkVaultNavigateToServer> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("chunk_vault_navigate"));
    public final static StreamCodec<RegistryFriendlyByteBuf, ChunkVaultNavigateToServer> STREAM_CODEC = BaniraStreamCodecs.registryBuf(ChunkVaultNavigateToServer::toBytes, ChunkVaultNavigateToServer::new);

    public ChunkVaultNavigateToServer(FriendlyByteBuf buf) {
        this(buf.readInt());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.offset());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChunkVaultNavigateToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                ChunkVaultSession.navigateOrReload(player, packet.offset());
            }
        });
    }
}
