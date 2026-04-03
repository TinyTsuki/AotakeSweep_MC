package xin.vanilla.aotake.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.network.NetworkPacket;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import java.util.function.Supplier;


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

    public static void handle(PlayerConfigSyncToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            PlayerSweepData data = PlayerSweepData.getData(player);
            data.setShowSweepResult(packet.showSweepResult);
            data.setEnableWarningVoice(packet.enableWarningVoice);
            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
