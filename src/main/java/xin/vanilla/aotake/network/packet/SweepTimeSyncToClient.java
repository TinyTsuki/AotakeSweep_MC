package xin.vanilla.aotake.network.packet;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.AotakePacket;

public record SweepTimeSyncToClient(long currentTime, long nextSweepTime, long sweepInterval) implements AotakePacket {
    public static final ResourceLocation ID = AotakeSweep.createIdentifier("sweep_time_sync");


    public SweepTimeSyncToClient() {
        this(System.currentTimeMillis(), ServerEventHandler.getNextSweepTime(), ServerConfig.get().sweepConfig().sweepInterval());
    }

    public SweepTimeSyncToClient(FriendlyByteBuf buf) {
        this(buf.readLong(), buf.readLong(), buf.readLong());
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
        AotakeSweep.clientServerTime().setKey(System.currentTimeMillis()).setValue(packet.currentTime());
        AotakeSweep.sweepTime().setKey(packet.sweepInterval()).setValue(packet.nextSweepTime());
    }
}
