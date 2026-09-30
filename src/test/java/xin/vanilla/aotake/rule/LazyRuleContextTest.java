package xin.vanilla.aotake.rule;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LazyRuleContextTest {
    @Test public void cachesNullsAndValuesPerEntityAndClearsReferences() {
        RuleSource source = LegacyRuleMigrationTest.source("v = [score] -> v == null || v > 0",
                RuleBindingPlan.defaults().withBinding("nbt", "score", Integer.class, true));
        LegacyRuleMigrationTest.CountingContext context = new LegacyRuleMigrationTest.CountingContext();
        context.reset(new HashMap<>(), source.bindings());
        assertNull(context.value(0, Integer.class)); assertNull(context.value(0, Integer.class));
        assertEquals(1, context.reads("score"));
        context.reset(Collections.singletonMap("score", 3), source.bindings());
        assertEquals(Integer.valueOf(3), context.value(0, Integer.class));
        assertEquals(2, context.reads("score"));
        assertThrows(IllegalArgumentException.class, () -> context.value(0, String.class));
        context.clear(); assertNull(context.data());
        assertThrows(IllegalStateException.class, () -> context.value(0, Integer.class));
    }
    @Test public void evaluationAlwaysReleasesEntityEvenWhenRuleFails() {
        LegacyRuleMigrationTest.CountingContext context = new LegacyRuleMigrationTest.CountingContext();
        RuleBindingPlan plan = LegacyRuleMigrationTest.source("tick -> tick > 0", RuleBindingPlan.defaults()).bindings();
        assertThrows(IllegalStateException.class, () -> context.evaluate(c -> {
            assertEquals(Integer.valueOf(4), c.value(0, Integer.class));
            throw new IllegalStateException("broken rule");
        }, Collections.singletonMap("tick", 4), plan));
        assertNull(context.data());
        assertThrows(IllegalStateException.class, () -> context.value(0, Integer.class));
    }
    @Test public void publicBuiltinsAlsoCacheWithoutDeclaredSlots() {
        LegacyRuleMigrationTest.CountingContext context = new LegacyRuleMigrationTest.CountingContext();
        context.reset(Collections.singletonMap("resource", "minecraft:item"), RuleBindingPlan.defaults());
        assertEquals("minecraft:item", context.resourceId());
        assertEquals("minecraft:item", context.resourceId());
        assertNull(context.customName()); assertNull(context.customName());
        assertEquals(1, context.reads("resource"));
        assertEquals(1, context.reads("customName"));
    }
}
