package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.multiplayer.ServerData;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.dev.AotakeRuleNetworkProbe;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.rule.RuleFileStore;
import xin.vanilla.aotake.rule.RuleMigrationTransaction;
import xin.vanilla.banira.common.config.*;
import xin.vanilla.banira.client.gui.ConfigEditorScreen;
import xin.vanilla.banira.common.network.packet.*;
import xin.vanilla.banira.common.util.PacketUtils;
import java.nio.file.Files;
import java.util.*;
import static xin.vanilla.aotake.internal.dev.AotakeRuleNetworkProbe.*;

public final class AotakeRuleNetworkSmokeClientRunner {
    private static int state, ticks;
    private static long start;
    private static ConfigHolder holder;
    private static ConfigEditSnapshot localBefore;
    private static Map<String, Object> remote = Collections.emptyMap();
    private AotakeRuleNetworkSmokeClientRunner() { }
    public static void tick(Minecraft client) {
        if (!enabled() || state == 5 || client.getOverlay() != null) return;
        try {
            if (start == 0) start = System.nanoTime();
            require(System.nanoTime() - start < 120_000_000_000L, "Client rule smoke timed out in state " + state);
            ticks++;
            if (state == 0 && ticks > 20) {
                ServerData data = new ServerData("Rule smoke", "127.0.0.1:" + System.getProperty("aotake.ruleNetworkSmoke.port"), false);
                client.setCurrentServer(data); client.setScreen(new ConnectingScreen(client.screen, client, data));
                state = 1;
            } else if (state == 1 && client.player != null && has("server", "READY configuration")) {
                require(client.getSingleplayerServer() == null && !client.getConnection().getConnection().isMemoryConnection(), "Expected remote dedicated server");
                holder = BaniraConfig.holder(CommonConfig.class);
                localBefore = holder.snapshotForEdit(holder.valuePaths());
                client.setScreen(new ConfigEditorScreen(holder, new ConfigEditorScreen.Args()) {
                    @Override public void applyRemoteSnapshot(String configName, Map<String, Object> snapshot) {
                        super.applyRemoteSnapshot(configName, snapshot);
                        remote = new LinkedHashMap<>(snapshot);
                    }
                });
                PacketUtils.sendPacketToServer(new ConfigFetchRequestToServer(holder.getConfigName()));
                state = 2;
            } else if (state == 2 && Objects.equals(remote.get(BATCH), 17)) {
                for (String path : RuleMigrationTransaction.LISTS) {
                    List<?> rules = (List<?>) remote.get(path);
                    require(rules.size() == 1 && rules.get(0).toString().startsWith("script:legacy_"), "Missing migrated client snapshot: " + path);
                }
                require(!Files.exists(RuleFileStore.local().root().resolve("rules/manifest.json")), "Client installed server Java files");
                require(!AotakeRuleRuntime.isUsable(1), "Client started executable rule runtime");
                status("client", "PASS remote-four-list-snapshot-no-rule-files-or-runtime");
                java.lang.reflect.Method sync = ConfigEditorScreen.class.getDeclaredMethod("syncToServerFull");
                sync.setAccessible(true);
                sync.invoke(client.screen);
                require(localBefore.getValues().equals(holder.snapshotForEdit(holder.valuePaths()).getValues()), "Full server sync changed local configuration");
                status("client", "PASS remote-editor-full-sync-keeps-local-values");
                Map<String,String> invalid = new LinkedHashMap<>();
                invalid.put(BATCH, "999");
                invalid.put(RuleMigrationTransaction.LISTS.get(0), ConfigSyncToServer.encodeConfigValue(Collections.singletonList("tick -> tick > 900")));
                PacketUtils.sendPacketToServer(new ConfigSyncToServer(holder.getConfigName(), invalid));
                PacketUtils.sendPacketToServer(new ConfigSyncToServer(holder.getConfigName(), Collections.singletonMap(BATCH, "23")));
                state = 3;
            } else if (state == 3 && has("server", "PASS remote-guard-and-save")) {
                PacketUtils.sendPacketToServer(new ConfigFetchRequestToServer(holder.getConfigName()));
                state = 4;
            } else if (state == 4 && Objects.equals(remote.get(BATCH), 23)) {
                require(((List<?>) remote.get(RuleMigrationTransaction.LISTS.get(0))).get(0).toString().startsWith("script:legacy_"), "Remote DSL replaced selected rule");
                require(!Files.exists(RuleFileStore.local().root().resolve("rules/manifest.json")), "Client created executable catalog after save");
                client.setScreen(null);
                ConfigEditSnapshot localAfter = holder.snapshotForEdit(holder.valuePaths());
                require(localBefore.getValues().equals(localAfter.getValues())
                        && Arrays.equals(localBefore.getSourceBytes(), localAfter.getSourceBytes()), "Remote snapshot polluted local runtime or disk configuration");
                status("client", "PASS remote-editor-close-preserves-local-config-bytes-and-values");
                status("client", "FINISHED rule-network-client");
                state = 5; client.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); status("client", "FAIL " + failure); state = 5; client.stop();
        }
    }
}
