package xin.vanilla.aotake.network.packet;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.event.EventHandlerProxy;

@Getter
public class SweepTimeSyncToClient implements CustomPacketPayload {
    public final static CustomPacketPayload.Type<SweepTimeSyncToClient> TYPE = new CustomPacketPayload.Type<>(AotakeSweep.createResource("sweep_time_sync"));
    public final static StreamCodec<ByteBuf, SweepTimeSyncToClient> STREAM_CODEC = new StreamCodec<>() {
        public @NotNull SweepTimeSyncToClient decode(@NotNull ByteBuf byteBuf) {
            return new SweepTimeSyncToClient((new FriendlyByteBuf(byteBuf)));
        }

        public void encode(@NotNull ByteBuf byteBuf, @NotNull SweepTimeSyncToClient packet) {
            packet.toBytes(new FriendlyByteBuf(byteBuf));
        }
    };

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

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SweepTimeSyncToClient packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AotakeSweep.getClientServerTime().setKey(System.currentTimeMillis()).setValue(packet.getCurrentTime());
            AotakeSweep.getSweepTime().setKey(packet.getSweepInterval()).setValue(packet.getNextSweepTime());
        });
    }
}
