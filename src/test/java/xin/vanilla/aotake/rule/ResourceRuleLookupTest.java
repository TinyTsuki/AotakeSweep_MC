package xin.vanilla.aotake.rule;

import com.google.gson.JsonObject;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.internal.neoforge.rule.NeoForgeEntityRuleContext;
import xin.vanilla.banira.internal.neoforge.platform.NeoForgeBaniraRegistryService;
import xin.vanilla.banira.platform.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ResourceRuleLookupTest {
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test public void longIdRunReadsRegistryOncePerMatchAndDoesNotCacheAcrossEntities() throws Exception {
        try (Registry fixture = new Registry()) {
            List<String> rules = new ArrayList<>();
            for (int i = 0; i < 127; i++) rules.add("probe:missing_" + i);
            rules.add("minecraft:item");
            RuleGeneration<Entity> generation = generation(rules, null);
            assertTrue(generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals("One lookup for the whole pure ID run", 1, fixture.lookups);
            fixture.key = ResourceLocation.parse("probe:changed");
            assertFalse(generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals(2, fixture.lookups);
        }
    }

    @Test public void scriptChangesInvalidateThePreviousIdRunKey() throws Exception {
        try (Registry fixture = new Registry()) {
            RuleGeneration<Entity> generation = generation(Arrays.asList(
                    "probe:before", "probe:before_again", "script:probe", "probe:after", "probe:changed"),
                    context -> { fixture.key = ResourceLocation.parse("probe:changed"); return false; });
            assertTrue(generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals("Two independent ID runs around the script", 2, fixture.lookups);
        }
    }

    @Test public void nestedMatchesDoNotReplaceTheOuterRunsKey() throws Exception {
        try (Registry fixture = new Registry()) {
            AtomicReference<RuleGeneration<Entity>> active = new AtomicReference<>();
            RuleGeneration<Entity> generation = generation(Arrays.asList(
                    "probe:a", "probe:b", "script:probe", "probe:c", "minecraft:item"), context -> {
                assertFalse(active.get().matches(RuleGeneration.RuleList.CHUNK, item()));
                return false;
            });
            active.set(generation);
            assertTrue(generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals("Outer two runs and one nested run", 3, fixture.lookups);
        }
    }

    @Test public void lookupRetirementStopsBeforeTheNextIdRule() throws Exception {
        try (Registry fixture = new Registry()) {
            RuleGeneration<Entity> generation = generation(Arrays.asList("probe:a", "minecraft:item"), null);
            fixture.afterLookup = () -> generation.retire("retired during lookup");
            RuleExecutionException failure = assertThrows(RuleExecutionException.class,
                    () -> generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertTrue(failure.isUnavailable());
            assertEquals(generation.id(), failure.generationId());
            assertFalse(generation.isUsable());
            assertEquals(1, fixture.lookups);
        }
    }

    @Test public void registryFailuresKeepTheCurrentRuleDiagnostic() throws Exception {
        try (Registry fixture = new Registry()) {
            RuleGeneration<Entity> generation = generation(Arrays.asList("probe:a", "minecraft:item"), null);
            fixture.afterLookup = () -> { throw new IllegalStateException("registry read failed"); };
            RuleExecutionException failure = assertThrows(RuleExecutionException.class,
                    () -> generation.matches(RuleGeneration.RuleList.SWEEP, item()));
            assertTrue(failure.getMessage().contains("Rule probe:a failed"));
            assertEquals("registry read failed", failure.getCause().getMessage());
            assertFalse(generation.isUsable());
        }
    }

    @Test public void wildcardAndUnknownKeysKeepTheExistingFallback() throws Exception {
        try (Registry fixture = new Registry()) {
            assertTrue(generation(Arrays.asList("probe:a", "minecraft:*"), null)
                    .matches(RuleGeneration.RuleList.SWEEP, item()));
            fixture.key = null;
            fixture.lookups = 0;
            assertTrue(generation(Arrays.asList("probe:a", "*:unknown_entity"), null)
                    .matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals("Missing registry keys retain the original three reads per fallback", 6, fixture.lookups);
        }
    }

    @Test public void singleIdDoesNotAddASecondRegistryLookup() throws Exception {
        try (Registry fixture = new Registry()) {
            assertTrue(generation(Collections.singletonList("minecraft:item"), null)
                    .matches(RuleGeneration.RuleList.SWEEP, item()));
            assertEquals(1, fixture.lookups);
        }
    }

    private static ItemEntity item() { return new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, null); }
    private static RuleGeneration<Entity> generation(List<String> rules, EntityRule script) {
        Map<String, Object> config = new HashMap<>();
        for (String path : RuleMigrationTransaction.LISTS) config.put(path, Arrays.asList("probe:nested_a", "probe:nested_b"));
        config.put(RuleMigrationTransaction.LISTS.get(0), rules);
        for (String path : RuleMigrationTransaction.MODES) config.put(path, "BLACK");
        Map<String, RuleSource> sources = script == null ? Collections.emptyMap()
                : Collections.singletonMap("probe", LegacyRuleMigrationTest.source("tick -> tick >= 0", RuleBindingPlan.defaults()));
        RuleCatalog catalog = new RuleCatalog(sources, Collections.emptyMap(), new JsonObject(), null);
        return new RuleGeneration<>(1, new RuleGeneration.Selection(config), catalog,
                script == null ? Collections.emptyMap() : Collections.singletonMap("probe", script), NeoForgeEntityRuleContext::new);
    }

    private static final class Registry implements AutoCloseable {
        private final Field field;
        private final Object previous;
        int lookups;
        ResourceLocation key = ResourceLocation.parse("minecraft:item");
        Runnable afterLookup = () -> {};
        Registry() throws Exception {
            field = BaniraPlatforms.class.getDeclaredField("platform"); field.setAccessible(true);
            previous = field.get(null);
            NeoForgeBaniraRegistryService actual = NeoForgeBaniraRegistryService.INSTANCE;
            BaniraRegistryService registry = (BaniraRegistryService) Proxy.newProxyInstance(
                    BaniraRegistryService.class.getClassLoader(), new Class<?>[]{BaniraRegistryService.class}, (proxy, method, args) -> {
                        if (method.getName().equals("entityTypeKeyObject")) {
                            assertSame(EntityType.ITEM, args[0]); lookups++; afterLookup.run(); return key;
                        }
                        try { return method.invoke(actual, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
            BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(BaniraPlatform.class.getClassLoader(),
                    new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                        if (method.getName().equals("registryService")) return registry;
                        throw new UnsupportedOperationException(method.toString());
                    }));
        }
        @Override public void close() throws Exception { field.set(null, previous); }
    }
}
