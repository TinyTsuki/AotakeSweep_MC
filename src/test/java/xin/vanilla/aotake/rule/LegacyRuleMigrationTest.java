package xin.vanilla.aotake.rule;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.rule.migration.*;
import xin.vanilla.banira.api.script.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class LegacyRuleMigrationTest {
    @Test public void existingNumericSlotSourceStillCompilesWithoutLayoutChanges() throws Exception {
        RuleSource generated = source("tick, score = [score] -> tick >= 5 && score != null && score > 1",
                RuleBindingPlan.defaults().withBinding("nbt", "score", Integer.class, true));
        String code = "package xin.vanilla.banira.generated; public final class LegacyProbe implements "
                + "xin.vanilla.aotake.api.rule.EntityRule { public boolean test("
                + "xin.vanilla.aotake.api.rule.EntityRuleContext context) { return "
                + "((Integer)context.value(0, Integer.class)) >= 5 && "
                + "context.value(1, Integer.class) != null && ((Integer)context.value(1, Integer.class)) > 1; }}";
        RuleSource legacy = new RuleSource(new ScriptSource("probe", "xin.vanilla.banira.generated.LegacyProbe",
                "LegacyProbe.java", code), generated.bindings(), generated.sourceHash(), Collections.emptyList());
        try (Fixture f = new Fixture(legacy)) {
            Map<String,Object> values = new HashMap<>(); values.put("tick", 5); values.put("score", 2);
            assertTrue(f.context.evaluate(f.rule, values, f.source.bindings()));
            values.put("score", 1);
            assertFalse(f.context.evaluate(f.rule, values, f.source.bindings()));
        }
    }

    @Test public void generatedBindingsHaveNamesAndBuiltinGetters() throws Exception {
        String text = "tick, class = [score], CLASS = [other] -> tick > 1 && class != null && CLASS != null && class < CLASS";
        RuleBindingPlan bindings = RuleBindingPlan.defaults().withBinding("nbt", "score", Integer.class, true)
                .withBinding("nbt", "other", Integer.class, true);
        RuleSource generated = source(text, bindings);
        String code = generated.script().getSource();
        assertTrue(code.contains("context.tick()"));
        assertFalse(code.matches("(?s).*context\\.value\\([0-9]+.*"));
        assertTrue(code.contains("SLOT_class"));
        assertTrue(code.contains("SLOT_CLASS"));
        try (Fixture f = compile(text, bindings)) {
            Map<String,Object> values = new HashMap<>(); values.put("tick", 5); values.put("score", 2); values.put("other", 3);
            f.context.reset(values, f.source.bindings());
            assertTrue(f.rule.test(f.context));
            assertEquals(Integer.valueOf(2), f.context.value(1, Integer.class));
        }
    }
    static final String CREATE = "tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> "
            + "tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)";
    static final String PROTECTION = "customName, hasOwner, createProcessing = [CreateData.Processing.Time] -> "
            + "customName != null || hasOwner || createProcessing > 0";

    @Test public void defaultRulesAreNullSafeAndUseLazyBindings() throws Exception {
        try (Fixture f = compile(CREATE, RuleBindingPlan.defaults())) {
            f.context.reset(new HashMap<>(), f.source.bindings());
            f.context.data().put("tick", 1);
            assertFalse(f.rule.test(f.context));
            assertEquals(0, f.context.reads("CreateData.Processing.Time"));
            f.context.data().put("tick", 8);
            f.context.data().put("clazz", String.class);
            f.context.data().put("itemClazz", Object.class);
            f.context.reset(f.context.data(), f.source.bindings());
            assertTrue(f.rule.test(f.context));
            assertEquals(1, f.context.reads("CreateData.Processing.Time"));
            assertFalse(f.source.adjustments().isEmpty());
        }
        try (Fixture f = compile(PROTECTION, RuleBindingPlan.defaults())) {
            Map<String,Object> data = new HashMap<>(); data.put("hasOwner", false);
            f.context.reset(data, f.source.bindings());
            assertFalse(f.rule.test(f.context));
            data.put("CreateData.Processing.Time", 2);
            f.context.reset(data, f.source.bindings());
            assertTrue(f.rule.test(f.context));
        }
    }

    @Test public void preservesQuotedCommasEscapesAndStringContentEquality() throws Exception {
        try (Fixture f = compile("v = 'a,b\\\\c\\\"d', resource -> v == resource", RuleBindingPlan.defaults())) {
            Map<String,Object> values = new HashMap<>(); values.put("resource", new String("a,b\\c\"d"));
            f.context.reset(values, f.source.bindings());
            assertTrue(f.rule.test(f.context));
        }
    }

    @Test public void usesJavaIntegerDivisionXorAndApprovedCalls() throws Exception {
        try (Fixture f = compile("resource -> 5 / 2 == 2 && (5 ^ 3) == 6 && Math.pow(2, 3) == 8 && resource.contains('craft')", RuleBindingPlan.defaults())) {
            f.context.reset(Collections.singletonMap("resource", "minecraft:arrow"), f.source.bindings());
            assertTrue(f.rule.test(f.context));
        }
        try (Fixture f = compile("clazz, itemClazz -> clazz :> itemClazz && itemClazz <: clazz", RuleBindingPlan.defaults())) {
            Map<String,Object> values = new HashMap<>(); values.put("clazz", String.class); values.put("itemClazz", Object.class);
            f.context.reset(values, f.source.bindings());
            assertTrue(f.rule.test(f.context));
        }
    }

    @Test public void rejectsAmbiguousTypesUnsafeNullsAndInvalidPrograms() {
        for (String text : Arrays.asList("tick -> '05' == 5", "tick,tick -> true", "missing -> true",
                "v = [unknown] -> v > 0", "tick -> tick.contains('x')", "tick -> unknown == 1",
                "tick -> System.exit(0)", "tick -> tick + true", "customName -> customName.contains('x')",
                "v = [unknown] -> v > 0")) {
            RuleDiagnostic error = assertThrows(text, RuleDiagnostic.class, () -> source(text, RuleBindingPlan.defaults()));
            assertTrue(error.offset() >= 0);
        }
        assertThrows(RuleDiagnostic.class, () -> source("v = {unsafe} -> v != null",
                RuleBindingPlan.defaults().withBinding("field", "unsafe", RuleBindingPlanTest.Holder.class, true)));
    }

    @Test public void explicitlyTypedDynamicValuesAndContainersCompile() throws Exception {
        RuleBindingPlan known = RuleBindingPlan.defaults().withBinding("nbt", "score", Long.class, true)
                .withBinding("field", "values", String[].class, true);
        try (Fixture f = compile("v = [score], a = {values} -> v != null && v > 2L && a != null && a.contains('ok')", known)) {
            Map<String,Object> values = new HashMap<>(); values.put("score", 3L); values.put("values", new String[]{"ok"});
            f.context.reset(values, f.source.bindings());
            assertTrue(f.rule.test(f.context));
            values.put("score", "3"); f.context.reset(values, f.source.bindings());
            assertThrows(IllegalStateException.class, () -> f.rule.test(f.context));
        }
    }

    @Test public void typedEqualityAndLargeLongsDoNotLosePrecision() throws Exception {
        try (Fixture f = compile("v = [number] -> v != null && v == 9007199254740993L && v != 9007199254740992L",
                RuleBindingPlan.defaults().withBinding("nbt", "number", Long.class, true))) {
            f.context.reset(Collections.singletonMap("number", 9007199254740993L), f.source.bindings());
            assertTrue(f.rule.test(f.context));
            assertEquals(1, f.context.reads("number"));
        }
        assertThrows(RuleDiagnostic.class, () -> source("tick -> 5 == null", RuleBindingPlan.defaults()));
    }
    @Test public void parserBoundsAndLiteralInjectionStayData() throws Exception {
        assertThrows(RuleDiagnostic.class, () -> source("tick -> " + String.join("", Collections.nCopies(140, "(")) + "true", RuleBindingPlan.defaults()));
        assertThrows(RuleDiagnostic.class, () -> source("tick -> " + String.join("+", Collections.nCopies(150, "1")) + " > 0", RuleBindingPlan.defaults()));
        try (Fixture f = compile("s = 'x\\\"); throw new RuntimeException(); //', tick -> s.contains('RuntimeException') && tick == 1", RuleBindingPlan.defaults())) {
            f.context.reset(Collections.singletonMap("tick", 1), f.source.bindings());
            assertTrue(f.rule.test(f.context));
        }
    }

    @Test public void nullableStringBranchUsesShortCircuit() throws Exception {
        try (Fixture f = compile("customName, tick -> (customName == null || !customName.contains('keep')) && tick > 3", RuleBindingPlan.defaults())) {
            Map<String,Object> values = new HashMap<>(); values.put("tick", 4);
            f.context.reset(values, f.source.bindings());
            assertTrue(f.rule.test(f.context));
            values.put("customName", "keep"); f.context.reset(values, f.source.bindings());
            assertFalse(f.rule.test(f.context));
        }
    }

    static RuleSource source(String text, RuleBindingPlan known) {
        RuleAst ast = LegacyRuleParser.parse(text);
        return JavaRuleEmitter.emit("probe", ast, RuleTypeResolver.resolve(ast, known));
    }
    static Fixture compile(String text, RuleBindingPlan known) throws Exception { return new Fixture(source(text, known)); }
    static final class Fixture implements AutoCloseable {
        final RuleSource source; final EntityRule rule; final ScriptSession<EntityRule> session;
        final CountingContext context = new CountingContext();
        Fixture(RuleSource source) throws Exception {
            this.source = source;
            session = BaniraScripts.open("aotake_sweep", EntityRule.class, "1", ScriptLimits.defaults(), Runnable::run);
            rule = session.prepare(Collections.singletonList(source.script())).get(10, TimeUnit.SECONDS).scripts().get("probe");
        }
        @Override public void close() { context.clear(); session.close(); }
    }
    static final class CountingContext extends LazyRuleContext<Map<String, Object>> {
        final Map<String,Integer> reads = new HashMap<>();
        Map<String,Object> data() { return subject(); }
        int reads(String key) { return reads.getOrDefault(key, 0); }
        @Override protected Object read(RuleBindingPlan.Binding binding) {
            reads.merge(binding.payload(), 1, Integer::sum);
            return subject().get(binding.payload());
        }
    }
}
