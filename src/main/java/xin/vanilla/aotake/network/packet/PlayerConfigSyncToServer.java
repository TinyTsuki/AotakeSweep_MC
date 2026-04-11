package xin.vanilla.aotake.network.packet;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.network.NetworkInit;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import java.util.function.Supplier;


public class PlayerConfigSyncToServer {

    private final boolean showSweepResult;
    private final boolean enableWarningVoice;

    public PlayerConfigSyncToServer(boolean showSweepResult, boolean enableWarningVoice) {
        this.showSweepResult = showSweepResult;
        this.enableWarningVoice = enableWarningVoice;
    }

    public PlayerConfigSyncToServer(PacketBuffer buf) {
        this.showSweepResult = buf.readBoolean();
        this.enableWarningVoice = buf.readBoolean();
    }

    public void toBytes(PacketBuffer buf) {
        buf.writeBoolean(this.showSweepResult);
        buf.writeBoolean(this.enableWarningVoice);
    }

    public static void handle(PlayerConfigSyncToServer packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity player = ctx.get().getSender();
            if (player == null) return;
            PlayerSweepData data = PlayerSweepData.getData(player);
            data.setShowSweepResult(packet.showSweepResult);
            data.setEnableWarningVoice(packet.enableWarningVoice);
            if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                PacketUtils.sendPacketToPlayer(NetworkInit.INSTANCE, new SweepDataSyncToClient(player), player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
