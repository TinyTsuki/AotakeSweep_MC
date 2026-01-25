package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.ServerEventHandler;

public record SweepTimeSyncToClient(long currentTime, long nextSweepTime,
                                    long sweepInterval) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SweepTimeSyncToClient> ID = new CustomPacketPayload.Type<>(AotakeSweep.createResource("sweep_time_sync"));
    public static final StreamCodec<FriendlyByteBuf, SweepTimeSyncToClient> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeLong(packet.currentTime);
                buf.writeLong(packet.nextSweepTime);
                buf.writeLong(packet.sweepInterval);
            },
            buf -> new SweepTimeSyncToClient(buf.readLong(), buf.readLong(), buf.readLong())
    );

    public SweepTimeSyncToClient() {
        this(System.currentTimeMillis(), ServerEventHandler.getNextSweepTime(), ServerConfig.get().sweepConfig().sweepInterval());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(SweepTimeSyncToClient packet) {
        AotakeSweep.clientServerTime().setKey(System.currentTimeMillis()).setValue(packet.currentTime());
        AotakeSweep.sweepTime().setKey(packet.sweepInterval()).setValue(packet.nextSweepTime());
    }
}
