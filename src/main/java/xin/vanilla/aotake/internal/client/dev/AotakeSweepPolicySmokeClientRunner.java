package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.multiplayer.ServerData;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.rule.RuleFileStore;
import xin.vanilla.aotake.rule.SweepPolicyCatalog;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.network.packet.ConfigFetchRequestToServer;
import xin.vanilla.banira.common.network.packet.ConfigSyncToServer;
import xin.vanilla.banira.common.util.PacketUtils;

import java.nio.file.Files;
import java.util.*;

import static xin.vanilla.aotake.internal.dev.AotakePolicySmokeProbe.*;

public final class AotakeSweepPolicySmokeClientRunner {
    private static int state, ticks;
    private static long started;
    private static ConfigHolder holder;
    private static ConfigEditSnapshot before;
    private static Map<String, Object> remote = Collections.emptyMap();

    private AotakeSweepPolicySmokeClientRunner() {
    }

    public static void tick(Minecraft client) {
        if (!enabled() || state == 99 || client.getOverlay() != null) return;
        try {
            if (started == 0) started = System.nanoTime();
            require(System.nanoTime() - started < 145_000_000_000L, "Policy client timeout state=" + state);
            ticks++;
            if (state == 0 && ticks > 20) {
                ServerData data = new ServerData("Policy smoke", "127.0.0.1:" + System.getProperty("aotake.policySmoke.port"), false);
                client.setCurrentServer(data);
                client.setScreen(new ConnectingScreen(client.screen, client, data));
                state = 1;
            } else if (state == 1 && client.player != null && client.player.hasPermissions(2)) {
                require(client.getSingleplayerServer() == null && !client.getConnection().getConnection().isMemoryConnection(), "Expected dedicated server connection");
                holder = BaniraConfig.holder(CommonConfig.class);
                before = holder.snapshotForEdit(holder.valuePaths());
                client.setScreen(new ConfigEditorScreen(holder, new ConfigEditorScreen.Args()) {
                    @Override
                    public void applyRemoteSnapshot(String name, Map<String, Object> values) {
                        super.applyRemoteSnapshot(name, values);
                        remote = new LinkedHashMap<>(values);
                    }
                });
                PacketUtils.sendPacketToServer(new ConfigFetchRequestToServer(holder.getConfigName()));
                state = 2;
            } else if (state == 2 && "smoke".equals(remote.get(SweepPolicyCatalog.CONFIG_PATH))) {
                java.lang.reflect.Method sync = ConfigEditorScreen.class.getDeclaredMethod("syncToServerFull");
                sync.setAccessible(true);
                sync.invoke(client.screen);
                Map<String, String> invalid = new LinkedHashMap<>();
                invalid.put("base.batch.sweepBatchLimit", "999");
                invalid.put(SweepPolicyCatalog.CONFIG_PATH, "unprepared_remote_policy");
                PacketUtils.sendPacketToServer(new ConfigSyncToServer(holder.getConfigName(), invalid));
                PacketUtils.sendPacketToServer(new ConfigSyncToServer(holder.getConfigName(), Collections.singletonMap("base.batch.sweepBatchLimit", "23")));
                state = 3;
                ticks = 0;
            } else if (state == 3 && ticks > 10) {
                PacketUtils.sendPacketToServer(new ConfigFetchRequestToServer(holder.getConfigName()));
                state = 4;
            } else if (state == 4 && Objects.equals(remote.get("base.batch.sweepBatchLimit"), 23)) {
                require("smoke".equals(remote.get(SweepPolicyCatalog.CONFIG_PATH)), "Remote unprepared policy replaced selection");
                require(!Files.exists(RuleFileStore.local().root().resolve("rules/policies/manifest.json")) && !AotakeRuleRuntime.isUsable(1), "Client installed server policy code/runtime");
                client.setScreen(null);
                ConfigEditSnapshot after = holder.snapshotForEdit(holder.valuePaths());
                require(before.getValues().equals(after.getValues()) && Arrays.equals(before.getSourceBytes(), after.getSourceBytes()), "Remote editing changed local config");
                status("client", "PASS policy-config-isolation");
                state = 5;
            } else if (state == 5 && has("server", "FINISHED policy-network-server")) {
                status("client", "FINISHED policy-network-client");
                state = 99;
                client.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            status("client", "FAIL " + failure);
            state = 99;
            client.stop();
        }
    }
}
