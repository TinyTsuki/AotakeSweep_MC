package xin.vanilla.aotake.rule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import static org.junit.Assert.*;
import static xin.vanilla.aotake.rule.RuleManagerTest.entity;

public class CleanupRuleGateTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void lastEntityRuleFailurePreventsAnyCleanupSideEffect() throws Exception {
        try (RuleManagerTest.Fixture f = new RuleManagerTest.Fixture(temporary.newFolder().toPath(), true)) {
            f.await(f.manager.reload());
            f.replaceSource(f.source().replace("return ", "if(context.tick()<0) throw new IllegalStateException(\"fail\"); return "));
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> rules = f.manager.acquire(RuleManager.RuleUse.RECYCLE);
            CleanupRuleGate<Map<String,Object>> gate = new CleanupRuleGate<>(rules, () -> f.manager.isUsable(rules.id()));
            List<Map<String,Object>> input = Arrays.asList(entity("test:item", 0), entity("test:item", -1));
            List<Map<String,Object>> recycled = new ArrayList<>();
            assertThrows(RuleExecutionException.class, () -> {
                List<CleanupRuleGate.Decision<Map<String,Object>>> ready = gate.prepare(input, value -> true, true);
                ready.forEach(value -> recycled.add(value.subject()));
            });
            assertTrue(recycled.isEmpty());
            assertFalse(f.manager.isUsable(rules.id()));
        }
    }

    @Test public void redlistAndCaptureAreDecidedBeforeCommitAndOldBatchCannotContinue() throws Exception {
        try (RuleManagerTest.Fixture f = new RuleManagerTest.Fixture(temporary.newFolder().toPath(), true)) {
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> rules = f.manager.acquire(RuleManager.RuleUse.RECYCLE);
            CleanupRuleGate<Map<String,Object>> gate = new CleanupRuleGate<>(rules, () -> f.manager.isUsable(rules.id()));
            List<CleanupRuleGate.Decision<Map<String,Object>>> ready = gate.prepare(
                    Arrays.asList(entity("test:item", 8), entity("test:animal", 8)),
                    value -> value.get("resource").equals("test:item"), true);
            assertFalse(ready.get(0).recycle());
            assertTrue(ready.get(1).recycle());
            f.await(f.manager.reload());
            assertThrows(RuleExecutionException.class, gate::requireUsable);
            assertThrows(RuleExecutionException.class, () -> gate.prepare(Collections.emptyList(), value -> true, false));
        }
    }
}
