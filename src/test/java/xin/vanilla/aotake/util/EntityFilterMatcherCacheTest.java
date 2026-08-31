package xin.vanilla.aotake.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class EntityFilterMatcherCacheTest {
    @Test
    public void reusesMatcherForEquivalentRules() {
        EntityFilter filter = new EntityFilter();
        List<String> first = Arrays.asList("minecraft:item", "minecraft:experience_orb");
        List<String> second = Arrays.asList("minecraft:item", "minecraft:experience_orb");

        assertSame(filter.compile(first), filter.compile(second));
    }

    @Test
    public void clearingRulesInvalidatesCompiledMatcher() {
        EntityFilter filter = new EntityFilter();
        List<String> rules = Arrays.asList("minecraft:item");
        EntityFilter.Matcher beforeClear = filter.compile(rules);

        filter.clear();

        assertNotSame(beforeClear, filter.compile(rules));
    }
}
