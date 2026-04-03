package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;


public class PlayerConfigSyncToServer implements NetworkPacket {

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

    public static void handle(PlayerConfigSyncToServer packet, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            PlayerSweepData data = PlayerSweepData.getData(player);
            data.setShowSweepResult(packet.showSweepResult);
            data.setEnableWarningVoice(packet.enableWarningVoice);
            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
