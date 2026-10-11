package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.minecraft.entity.player.ServerPlayerEntity;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.aotake.network.SweepScheduleSync;
import xin.vanilla.aotake.client.minimap.AotakeMinimapCountdown;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.banira.common.network.BaniraNetworkContext;
import xin.vanilla.banira.common.network.BaniraPacketBuffer;

@Getter
public class SweepDataSyncToClient implements NetworkPacket {
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
        this.sweepInterval = CommonConfig.get().base().sweep().sweepInterval();
        this.nextSweepTime = SweepScheduleSync.effectiveNextSweep(EventHandlerProxy.getNextSweepTime(),
                sweepInterval, AotakeSweep.isDisable(), AotakeRuleRuntime.hasCompletedInitialLoad());
        PlayerSweepData data = PlayerSweepData.getData(player);
        this.showSweepResult = data.isShowSweepResult();
        this.enableWarningVoice = data.isEnableWarningVoice();
    }

    public SweepDataSyncToClient(BaniraPacketBuffer buf) {
        this.currentTime = buf.readLong();
        this.nextSweepTime = buf.readLong();
        this.sweepInterval = buf.readLong();
        this.showSweepResult = buf.readBoolean();
        this.enableWarningVoice = buf.readBoolean();
    }

    public void toBytes(BaniraPacketBuffer buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
        buf.writeBoolean(this.showSweepResult);
        buf.writeBoolean(this.enableWarningVoice);
    }

    public static void handle(SweepDataSyncToClient packet, BaniraNetworkContext ctx) {
        if (!ctx.isClientSide()) {
            ctx.markHandled();
            return;
        }
        AotakeMinimapCountdown.accept(ctx, packet.getCurrentTime(), packet.getNextSweepTime(), packet.getSweepInterval(), () -> {
            AotakeSweep.getClientServerTime().key(System.currentTimeMillis()).value(packet.getCurrentTime());
            AotakeSweep.getSweepTime().key(packet.getSweepInterval()).value(packet.getNextSweepTime());
            AotakeSweep.setClientCachedPlayerSweepPrefs(packet.isShowSweepResult(), packet.isEnableWarningVoice());
        });
        ctx.markHandled();
    }
}
