package xin.vanilla.aotake.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.network.packet.SweepDataSyncToClient;
import xin.vanilla.banira.common.util.PacketUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

public final class SweepScheduleSync {
    private SweepScheduleSync() {
    }

    public static long effectiveNextSweep(long deadline, long interval, boolean disabled, boolean rulesReady) {
        return !disabled && rulesReady && interval > 0 && deadline > 0 ? deadline : 0;
    }

    public static void broadcast(MinecraftServer server) {
        if (server == null) return;
        server.execute(() -> {
            if (!server.isRunning()) return;
            for (ServerPlayerEntity player : server.getPlayerList().getPlayers()) {
                if (PlayerUtils.isRemoteClientModInstalled(player, AotakeSweep.MODID)) {
                    PacketUtils.sendPacketToPlayer(new SweepDataSyncToClient(player), player);
                }
            }
        });
    }
}
