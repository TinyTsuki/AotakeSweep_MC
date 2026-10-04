package xin.vanilla.aotake.rule;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityRule;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class RuleGenerationReentryTest {
    @Test public void swallowedNestedFailureCannotReturnAResultFromARetiredGeneration() {
        for (boolean result : new boolean[]{true, false}) {
            RuleSource source = LegacyRuleMigrationTest.source("tick -> tick >= 0", RuleBindingPlan.defaults());
            RuleCatalog catalog = new RuleCatalog(Collections.singletonMap("probe", source),
                    Collections.emptyMap(), new JsonObject(), null);
            Map<String, Object> config = new HashMap<>();
            for (String path : RuleMigrationTransaction.LISTS) config.put(path, Collections.singletonList("script:probe"));
            for (String path : RuleMigrationTransaction.MODES) config.put(path, "BLACK");
            AtomicReference<RuleGeneration<Map<String, Object>>> current = new AtomicReference<>();
            EntityRule rule = context -> {
                if (context.tick() < 0) throw new IllegalStateException("nested failure");
                try { current.get().matches(RuleGeneration.RuleList.SWEEP, RuleManagerTest.entity("test:item", -1)); }
                catch (RuleExecutionException expected) { }
                return result;
            };
            List<RuleManagerTest.Context> contexts = new ArrayList<>();
            RuleGeneration<Map<String, Object>> generation = new RuleGeneration<>(1,
                    new RuleGeneration.Selection(config), catalog, Collections.singletonMap("probe", rule), plan -> {
                        RuleManagerTest.Context context = new RuleManagerTest.Context();
                        contexts.add(context);
                        return context;
                    });
            current.set(generation);
            assertThrows(RuleExecutionException.class, () -> generation.matches(
                    RuleGeneration.RuleList.SWEEP, RuleManagerTest.entity("test:item", 1)));
            assertFalse(generation.isUsable());
            assertTrue(contexts.stream().allMatch(RuleManagerTest.Context::released));
        }
    }
}
