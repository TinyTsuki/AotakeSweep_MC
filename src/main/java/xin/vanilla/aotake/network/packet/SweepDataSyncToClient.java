package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.EventHandlerProxy;

import java.util.function.Supplier;

@Getter
public class SweepDataSyncToClient {
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
    /**
     * 与 {@link PlayerSweepData} 一致，供客户端偏好界面使用（由 {@link SweepDataSyncToClient} 更新 {@link AotakeSweep} 侧缓存）。
     */
    private final boolean showSweepResult;
    private final boolean enableWarningVoice;

    public SweepDataSyncToClient(ServerPlayerEntity player) {
        this.currentTime = System.currentTimeMillis();
        this.nextSweepTime = EventHandlerProxy.getNextSweepTime();
        this.sweepInterval = CommonConfig.get().base().sweep().sweepInterval();
        PlayerSweepData data = PlayerSweepData.getData(player);
        this.showSweepResult = data.isShowSweepResult();
        this.enableWarningVoice = data.isEnableWarningVoice();
    }

    public SweepDataSyncToClient(PacketBuffer buf) {
        this.currentTime = buf.readLong();
        this.nextSweepTime = buf.readLong();
        this.sweepInterval = buf.readLong();
        if (buf.readableBytes() >= 2) {
            this.showSweepResult = buf.readBoolean();
            this.enableWarningVoice = buf.readBoolean();
        } else {
            this.showSweepResult = true;
            this.enableWarningVoice = true;
        }
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
        buf.writeBoolean(this.showSweepResult);
        buf.writeBoolean(this.enableWarningVoice);
    }

    public static void handle(SweepDataSyncToClient packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            AotakeSweep.getClientServerTime().key(System.currentTimeMillis()).value(packet.getCurrentTime());
            AotakeSweep.getSweepTime().key(packet.getSweepInterval()).value(packet.getNextSweepTime());
            AotakeSweep.setClientCachedPlayerSweepPrefs(packet.isShowSweepResult(), packet.isEnableWarningVoice());
        });
        ctx.get().setPacketHandled(true);
    }
}
