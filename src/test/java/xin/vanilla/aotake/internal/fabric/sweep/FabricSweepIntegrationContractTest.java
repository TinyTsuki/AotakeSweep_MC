package xin.vanilla.aotake.internal.fabric.sweep;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class FabricSweepIntegrationContractTest {
    private String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/xin/vanilla/aotake/" + path)), StandardCharsets.UTF_8);
    }

    @Test public void startupStopAndWorldTickOwnTheCompleteSweepRuntime() throws Exception {
        String bootstrap = source("AotakeSweep.java");
        assertTrue("rule runtime must start", bootstrap.contains("AotakeRuleRuntime.start("));
        assertTrue("sweep runtime must start", bootstrap.contains("AotakeSweepRuntime.start("));
        assertTrue("sweep runtime must stop", bootstrap.contains("AotakeSweepRuntime.stop()"));
        assertTrue("rule runtime must stop", bootstrap.contains("AotakeRuleRuntime.stop()"));
        String events = source("event/EventHandlerProxy.java");
        assertTrue("pending tasks advance before disable check", events.indexOf("AotakeSweepRuntime.tick()") < events.indexOf("if (AotakeSweep.isDisable()) return;"));
        assertTrue("world START tick flushes removals", events.contains("EntitySweeper.flushPendingRemovals"));
    }

    @Test public void oldExpressionFilterHasNoProductionEntryPoint() throws Exception {
        for (String file : new String[]{"AotakeSweep.java", "util/AotakeUtils.java", "util/EntitySweeper.java", "event/EventHandlerProxy.java"}) {
            String text = source(file);
            assertFalse(file, text.contains("getEntityFilter()"));
            assertFalse(file, text.contains("SafeExpressionEvaluator"));
            assertFalse(file, text.contains("import xin.vanilla.aotake.util.EntityFilter;"));
        }
        assertFalse(Files.exists(Paths.get("src/main/java/xin/vanilla/aotake/util/EntityFilter.java")));
    }

    @Test public void modernRemovalAdapterUsesDiscardWithoutDeathOrLegacyDespawn() throws Exception {
        String text = source("internal/platform/EntityRemovalBridge.java");
        assertTrue(text.contains("ServerLevel world"));
        assertTrue(text.contains("entity.discard()"));
        assertFalse(text.contains("world.despawn("));
        assertFalse(text.contains("RemovalReason.KILLED"));
    }

    @Test public void reloadCommandAndLoadedOnlyQueriesAreWired() throws Exception {
        assertTrue(source("command/impl/ConfigCommand.java").contains("RuleReloadCommand.register()"));
        String world = source("internal/fabric/rule/FabricWorldAccess.java");
        assertTrue(world.contains("getChunkNow("));
        assertFalse(world.contains(".getChunk("));
        assertTrue(world.contains("Double.isFinite"));
        assertTrue(world.contains("entityGuard"));
    }

    @Test public void removalLedgerRetainsAlreadyRemovedButStillIndexedEntities() throws Exception {
        String sweeper = source("util/EntitySweeper.java");
        assertFalse("Alive flag alone cannot acknowledge native removal", sweeper.contains("if (!entity.isAlive()) return true;"));
        assertTrue(sweeper.contains("EntityRemovalBridge.isComplete(world, entity)"));
        String bridge = source("internal/platform/EntityRemovalBridge.java");
        assertTrue(bridge.contains("entity.isRemoved()"));
        assertTrue(bridge.contains("world.getEntity(entity.getId())"));
        assertTrue(bridge.contains("world.getEntity(entity.getUUID())"));
    }
}
