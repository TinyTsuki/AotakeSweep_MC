package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.EventHandlerProxy;

import java.util.function.Supplier;

public record SweepTimeSyncToClient(long currentTime, long nextSweepTime, long sweepInterval) {

    public SweepTimeSyncToClient() {
        this(System.currentTimeMillis(), EventHandlerProxy.getNextSweepTime(), ServerConfig.SWEEP_INTERVAL.get());
    }

    public SweepTimeSyncToClient(FriendlyByteBuf buf) {
        this(buf.readLong(), buf.readLong(), buf.readLong());
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
    }

    public static void handle(SweepTimeSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AotakeSweep.getClientServerTime().setKey(System.currentTimeMillis()).setValue(packet.currentTime());
            AotakeSweep.getSweepTime().setKey(packet.sweepInterval()).setValue(packet.nextSweepTime());
        });
        ctx.get().setPacketHandled(true);
    }
}
