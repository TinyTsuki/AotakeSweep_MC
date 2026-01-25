package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.AotakePacket;

@Getter
public class SweepTimeSyncToClient implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createResource("sweep_time_sync");

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
        this.nextSweepTime = ServerEventHandler.getNextSweepTime();
        this.sweepInterval = ServerConfig.get().sweepConfig().sweepInterval();
    }

    public SweepTimeSyncToClient(FriendlyByteBuf buf) {
        this.currentTime = buf.readLong();
        this.nextSweepTime = buf.readLong();
        this.sweepInterval = buf.readLong();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public FriendlyByteBuf toBytes(FriendlyByteBuf buf) {
        if (buf == null) buf = PacketByteBufs.create();
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
        return buf;
    }

    public static void handle(SweepTimeSyncToClient packet) {
        AotakeSweep.clientServerTime().setKey(System.currentTimeMillis()).setValue(packet.getCurrentTime());
        AotakeSweep.sweepTime().setKey(packet.getSweepInterval()).setValue(packet.getNextSweepTime());
    }
}
