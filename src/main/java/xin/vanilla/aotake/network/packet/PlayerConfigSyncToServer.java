package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.Identifier;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;
import xin.vanilla.banira.internal.network.BaniraStreamCodecs;


public record PlayerConfigSyncToServer(boolean showSweepResult, boolean enableWarningVoice) implements NetworkPacket {
    public final static CustomPacketPayload.Type<PlayerConfigSyncToServer> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("player_config_sync"));
    public final static StreamCodec<RegistryFriendlyByteBuf, PlayerConfigSyncToServer> STREAM_CODEC = BaniraStreamCodecs.registryBuf(PlayerConfigSyncToServer::toBytes, PlayerConfigSyncToServer::new);

    public PlayerConfigSyncToServer(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.showSweepResult());
        buf.writeBoolean(this.enableWarningVoice());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerConfigSyncToServer packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PlayerSweepData data = PlayerSweepData.getData(player);
                data.setShowSweepResult(packet.showSweepResult());
                data.setEnableWarningVoice(packet.enableWarningVoice());
                if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                    PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
                }
            }
        });
    }
}
