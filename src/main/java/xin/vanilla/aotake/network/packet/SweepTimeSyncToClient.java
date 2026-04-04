package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.event.EventHandlerProxy;

import java.util.function.Supplier;

@Getter
public class SweepTimeSyncToClient {
    /**
     * 当前时间
     */
    private final long currentTime;

    /**
     * 下次清理时间
     */
    private final long nextSweepTime;

    /**
     * 扫地间隔
     */
    private final long sweepInterval;


    public SweepTimeSyncToClient() {
        this.currentTime = System.currentTimeMillis();
        this.nextSweepTime = EventHandlerProxy.getNextSweepTime();
        this.sweepInterval = CommonConfig.get().base().sweep().sweepInterval();
    }

    public SweepTimeSyncToClient(PacketBuffer buf) {
        this.currentTime = buf.readLong();
        this.nextSweepTime = buf.readLong();
        this.sweepInterval = buf.readLong();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
    }

    public static void handle(SweepTimeSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AotakeSweep.getClientServerTime().key(System.currentTimeMillis()).value(packet.getCurrentTime());
            AotakeSweep.getSweepTime().key(packet.getSweepInterval()).value(packet.getNextSweepTime());
        });
        ctx.get().setPacketHandled(true);
    }
}
