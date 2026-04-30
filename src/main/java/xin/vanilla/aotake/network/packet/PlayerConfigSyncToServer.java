package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.network.AotakeNetworkPacket;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.network.NetworkContext;
import xin.vanilla.banira.common.util.PlayerUtils;

public class PlayerConfigSyncToServer implements AotakeNetworkPacket {

    private final boolean showSweepResult;
    private final boolean enableWarningVoice;

    public PlayerConfigSyncToServer(boolean showSweepResult, boolean enableWarningVoice) {
        this.showSweepResult = showSweepResult;
        this.enableWarningVoice = enableWarningVoice;
    }

    public PlayerConfigSyncToServer(FriendlyByteBuf buf) {
        this.showSweepResult = buf.readBoolean();
        this.enableWarningVoice = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.showSweepResult);
        buf.writeBoolean(this.enableWarningVoice);
    }

    public boolean showSweepResult() {
        return showSweepResult;
    }

    public boolean enableWarningVoice() {
        return enableWarningVoice;
    }

    public static void handle(PlayerConfigSyncToServer packet, NetworkContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ctx.isServerSide()) {
                return;
            }
            ServerPlayer player = ctx.sender();
            if (player == null) {
                return;
            }
            PlayerSweepData data = PlayerSweepData.getData(player);
            data.setShowSweepResult(packet.showSweepResult);
            data.setEnableWarningVoice(packet.enableWarningVoice);
            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                AotakeUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
            }
        });
    }
}
