package xin.vanilla.aotake.rule;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class FullRuleMigrationContractTest {
    private static String source(String relative) throws Exception {
        Path path = Paths.get("src/main/java/xin/vanilla/aotake", relative);
        assertTrue("Missing migration source: " + relative, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void fullRuleAndSweepLifecycleIsWired() throws Exception {
        assertTrue(source("internal/server/AotakeRuleRuntime.java").contains("BaniraScripts.openFactorySession"));
        assertTrue(source("internal/server/AotakeSweepRuntime.java").contains("SweepTaskFactory.create"));
        String entry = source("AotakeSweep.java");
        assertTrue(entry.contains("AotakeRuleRuntime.start"));
        assertTrue(entry.contains("AotakeRuleRuntime.stop"));
        assertTrue(entry.contains("AotakeSweepRuntime.stop"));
        String events = source("event/EventHandlerProxy.java");
        assertTrue(events.contains("AotakeSweepRuntime.tick()"));
        assertTrue(events.contains("AotakeSweepRuntime.submitChunk()"));
        assertFalse(events.contains("chunkSweepLock"));
        assertTrue(source("command/impl/ConfigCommand.java").contains("RuleReloadCommand.register()"));
    }

    @Test
    public void productionUsesMigratableBuiltinsAndPolicy() throws Exception {
        String config = source("config/CommonConfig.java");
        assertTrue(config.contains("sweepPolicyId"));
        assertTrue(config.contains("BuiltinRules.CLEANUP"));
        assertTrue(config.contains("BuiltinRules.PROTECTION"));
        assertFalse(Files.exists(Paths.get("src/main/java/xin/vanilla/aotake/util/EntityFilter.java")));
        assertFalse(source("util/AotakeUtils.java").contains("getEntityFilter"));
        assertTrue(source("util/EntitySweeper.java").contains("RemovalLedger"));
        assertTrue(source("util/EntitySweeper.java").contains("RecyclingBatch"));
        assertTrue(source("rule/RuleManager.java").contains("RuleMigrationTransaction"));
        assertTrue(source("sweep/SweepTask.java").contains("release"));
    }
}
