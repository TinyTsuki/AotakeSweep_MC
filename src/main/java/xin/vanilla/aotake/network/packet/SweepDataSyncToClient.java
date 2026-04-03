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
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.internal.network.BaniraStreamCodecs;

public record SweepDataSyncToClient(long currentTime, long nextSweepTime, long sweepInterval, boolean showSweepResult, boolean enableWarningVoice) implements NetworkPacket {
    public final static CustomPacketPayload.Type<SweepDataSyncToClient> TYPE = new CustomPacketPayload.Type<>(Identifier.id().create("sweep_data_sync"));
    public final static StreamCodec<RegistryFriendlyByteBuf, SweepDataSyncToClient> STREAM_CODEC = BaniraStreamCodecs.registryBuf(SweepDataSyncToClient::toBytes, SweepDataSyncToClient::new);

    public SweepDataSyncToClient(ServerPlayer player) {
        this(System.currentTimeMillis()
                , EventHandlerProxy.getNextSweepTime()
                , CommonConfig.get().base().sweep().sweepInterval()
                , PlayerSweepData.getData(player).isShowSweepResult()
                , PlayerSweepData.getData(player).isEnableWarningVoice());
    }

    public SweepDataSyncToClient(FriendlyByteBuf buf) {
        this(buf.readLong(), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readBoolean());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(this.currentTime());
        buf.writeLong(this.nextSweepTime());
        buf.writeLong(this.sweepInterval());
        buf.writeBoolean(this.showSweepResult());
        buf.writeBoolean(this.enableWarningVoice());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SweepDataSyncToClient packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AotakeSweep.getClientServerTime().key(System.currentTimeMillis()).value(packet.currentTime());
            AotakeSweep.getSweepTime().key(packet.sweepInterval()).value(packet.nextSweepTime());
            AotakeSweep.setClientCachedPlayerSweepPrefs(packet.showSweepResult(), packet.enableWarningVoice());
        });
    }
}
