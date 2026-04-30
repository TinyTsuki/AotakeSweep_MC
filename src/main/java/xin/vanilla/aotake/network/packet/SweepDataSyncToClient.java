package xin.vanilla.aotake.network.packet;

import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.event.ServerEventHandler;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.banira.common.network.NetworkContext;

@Getter
public class SweepDataSyncToClient implements AotakeNetworkPacket {

    private final long currentTime;
    private final long nextSweepTime;
    private final long sweepInterval;

    private final boolean showSweepResult;
    private final boolean enableWarningVoice;

    public SweepDataSyncToClient(ServerPlayer player) {
        this.currentTime = System.currentTimeMillis();
        this.nextSweepTime = ServerEventHandler.getNextSweepTime();
        this.sweepInterval = CommonConfig.get().base().sweep().sweepInterval();
        PlayerSweepData data = PlayerSweepData.getData(player);
        this.showSweepResult = data.isShowSweepResult();
        this.enableWarningVoice = data.isEnableWarningVoice();
    }

    public SweepDataSyncToClient(FriendlyByteBuf buf) {
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

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeLong(this.currentTime);
        buf.writeLong(this.nextSweepTime);
        buf.writeLong(this.sweepInterval);
        buf.writeBoolean(this.showSweepResult);
        buf.writeBoolean(this.enableWarningVoice);
    }

    public static void handle(SweepDataSyncToClient packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isClientSide()) {
                return;
            }
            AotakeSweep.clientServerTime().key(System.currentTimeMillis()).value(packet.getCurrentTime());
            AotakeSweep.sweepTime().key(packet.getSweepInterval()).value(packet.getNextSweepTime());
            AotakeSweep.setClientCachedPlayerSweepPrefs(packet.isShowSweepResult(), packet.isEnableWarningVoice());
        });
    }
}
