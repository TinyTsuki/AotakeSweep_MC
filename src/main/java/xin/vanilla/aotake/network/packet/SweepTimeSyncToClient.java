package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
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
        this.sweepInterval = ServerConfig.SWEEP_INTERVAL.get();
    }

    public SweepTimeSyncToClient(FriendlyByteBuf buf) {
        this.currentTime = buf.readLong();
        this.nextSweepTime = buf.readLong();
        this.sweepInterval = buf.readLong();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
    }

    public static void handle(SweepTimeSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AotakeSweep.getClientServerTime().setKey(System.currentTimeMillis()).setValue(packet.getCurrentTime());
            AotakeSweep.getSweepTime().setKey(packet.getSweepInterval()).setValue(packet.getNextSweepTime());
        });
        ctx.get().setPacketHandled(true);
    }
}
