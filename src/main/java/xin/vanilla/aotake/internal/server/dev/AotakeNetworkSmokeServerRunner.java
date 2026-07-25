package xin.vanilla.aotake.internal.server.dev;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.player.PlayerSweepData;
import xin.vanilla.aotake.data.world.WorldTrashData;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.banira.api.BaniraServer;

import java.util.List;

/** 在独立服务端内写入并复核网络 smoke 的持久化哨兵。 */
public final class AotakeNetworkSmokeServerRunner {
    private static final int SENTINEL_COUNT = 7;
    private static final int SENTINEL_CONFIG_VALUE = 11;

    private static boolean ready;
    private static boolean commandVerified;
    private static boolean finished;
    private static int commandRefreshStage;
    private static int commandRefreshWaitTicks;
    private static int shutdownTicks;

    private AotakeNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (AotakeNetworkSmokeStatus.enabled()) {
            MinecraftForge.EVENT_BUS.addListener(AotakeNetworkSmokeServerRunner::onServerTick);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            MinecraftServer server = BaniraServer.currentAs(MinecraftServer.class);
            if (server == null || !server.isRunning()) {
                return;
            }
            if (finished) {
                shutdownWhenSaved(server);
                return;
            }
            if (!ready) {
                ready = true;
                AotakeNetworkSmokeStatus.append("PASS server-ready");
            }
            List<ServerPlayerEntity> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return;
            }
            ServerPlayerEntity player = players.get(0);
            if ("phase-one".equals(AotakeNetworkSmokeStatus.phase())) {
                runWritePhase(player);
            } else if ("phase-two".equals(AotakeNetworkSmokeStatus.phase())) {
                runVerifyPhase(player);
            } else {
                throw new IllegalStateException("Unknown network smoke phase: " + AotakeNetworkSmokeStatus.phase());
            }
        } catch (Throwable error) {
            finished = true;
            AotakeNetworkSmokeStatus.append("FAIL server " + error);
            throw error;
        }
    }

    /** 留出玩家退出和数据落盘时间，再走 Minecraft 自身的正常关闭流程。 */
    private static void shutdownWhenSaved(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() > 0) {
            shutdownTicks = 0;
            return;
        }
        if (++shutdownTicks >= 40) {
            AotakeNetworkSmokeStatus.append("PASS server-shutdown");
            server.halt(false);
        }
    }

    private static void runWritePhase(ServerPlayerEntity player) {
        if (!commandVerified) {
            if (!verifyConciseCommandRefresh(player)) {
                return;
            }
            int commandResult = player.getServer().getCommands().performCommand(
                    player.createCommandSourceStack().withPermission(4),
                    "aotake config common base.batch.sweepBatchLimit " + SENTINEL_CONFIG_VALUE);
            if (commandResult <= 0 || CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
                throw new IllegalStateException("Config command did not update sweepBatchLimit");
            }
            AotakeNetworkSmokeStatus.append("PASS config-command-roundtrip");
            commandVerified = true;
        }

        PlayerSweepData playerData = PlayerSweepData.getData(player);
        if (playerData.isShowSweepResult() || playerData.isEnableWarningVoice()) {
            return;
        }
        WorldTrashData.getTrashContainer(player, 1);
        WorldTrashData trashData = WorldTrashData.get(player);
        Inventory firstPage = trashData.getInventoryList().get(0);
        firstPage.setItem(0, new ItemStack(Items.DIAMOND, SENTINEL_COUNT));
        trashData.setDirty();
        AotakeNetworkSmokeStatus.append("PASS server-config-roundtrip");
        AotakeNetworkSmokeStatus.append("PASS phase-one-world-write");
        AotakeNetworkSmokeStatus.append("FINISHED phase-one");
        finished = true;
    }

    /**
     * 通过真实配置指令触发与配置界面同步相同的 save listener，并检查命令树增删。
     */
    private static boolean verifyConciseCommandRefresh(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        String rootName = CommonConfig.get().command().commandClearDrop();
        boolean registered = server.getCommands().getDispatcher().getRoot().getChild(rootName) != null;
        boolean enabled = CommonConfig.get().concise().conciseClearDrop();

        if (++commandRefreshWaitTicks > 100) {
            throw new IllegalStateException("Timed out waiting for concise command tree refresh");
        }
        switch (commandRefreshStage) {
            case 0:
                runConfigCommand(player, "concise.conciseClearDrop", true);
                commandRefreshStage = 1;
                return false;
            case 1:
                if (!enabled || !registered) {
                    return false;
                }
                runConfigCommand(player, "concise.conciseClearDrop", false);
                commandRefreshStage = 2;
                return false;
            case 2:
                if (enabled || registered) {
                    return false;
                }
                runConfigCommand(player, "concise.conciseClearDrop", true);
                commandRefreshStage = 3;
                return false;
            case 3:
                if (!enabled || !registered) {
                    return false;
                }
                AotakeNetworkSmokeStatus.append("PASS concise-command-live-refresh");
                return true;
            default:
                throw new IllegalStateException("Unknown command refresh stage: " + commandRefreshStage);
        }
    }

    private static void runConfigCommand(ServerPlayerEntity player, String path, boolean value) {
        int result = player.getServer().getCommands().performCommand(
                player.createCommandSourceStack().withPermission(4),
                "aotake config common " + path + " " + value);
        if (result <= 0) {
            throw new IllegalStateException("Config command failed for " + path + "=" + value);
        }
        commandRefreshWaitTicks = 0;
    }

    private static void runVerifyPhase(ServerPlayerEntity player) {
        if (CommonConfig.get().base().batch().sweepBatchLimit() != SENTINEL_CONFIG_VALUE) {
            throw new IllegalStateException("Command config value was not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-command-config");

        PlayerSweepData playerData = PlayerSweepData.getData(player);
        if (playerData.isShowSweepResult() || playerData.isEnableWarningVoice()) {
            throw new IllegalStateException("Player preferences were not restored from disk");
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-player-data");

        WorldTrashData trashData = WorldTrashData.get(player);
        if (trashData.getInventoryList().isEmpty()) {
            throw new IllegalStateException("Persisted dustbin page is missing");
        }
        ItemStack stack = trashData.getInventoryList().get(0).getItem(0);
        if (stack.getItem() != Items.DIAMOND || stack.getCount() != SENTINEL_COUNT) {
            throw new IllegalStateException("Persisted dustbin sentinel is missing: " + stack);
        }
        AotakeNetworkSmokeStatus.append("PASS persisted-world-data");
        AotakeNetworkSmokeStatus.append("FINISHED phase-two");
        finished = true;
    }
}
