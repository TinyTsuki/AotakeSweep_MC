package xin.vanilla.aotake.rule;

import org.junit.Test;
import xin.vanilla.aotake.internal.forge.rule.ForgeRuleBindings;
import java.util.*;
import static org.junit.Assert.*;

public class RuleBindingPlanTest {
    public static class Holder { public int count = 7; public List<String> names = Arrays.asList("a", "b"); }
    public static class Other { }
    public static class Nested { public Optional<Holder> child = Optional.of(new Holder()); }
    @Test public void optionalSubtypeBindingDoesNotFaultMixedEntityScans() {
        RuleBindingPlan.Binding optional = new RuleBindingPlan.Binding("v", "field",
                Holder.class.getName() + ":count", Integer.class, true, 0);
        ForgeRuleBindings.Reader reader = ForgeRuleBindings.field(optional, Object.class, getClass().getClassLoader());
        assertEquals(7, reader.read(new Holder()));
        assertNull("An unrelated subject has no optional subtype field", reader.read(new Other()));
        assertEquals("A skipped subject must not poison the next read", 7, reader.read(new Holder()));
        RuleBindingPlan.Binding required = new RuleBindingPlan.Binding("v", "field",
                Holder.class.getName() + ":count", Integer.class, false, 0);
        ForgeRuleBindings.Reader strict = ForgeRuleBindings.field(required, Object.class, getClass().getClassLoader());
        assertThrows(IllegalStateException.class, () -> strict.read(new Other()));
        assertThrows(RuleDiagnostic.class, () -> ForgeRuleBindings.field(new RuleBindingPlan.Binding("v", "field",
                Holder.class.getName() + ":missing", Integer.class, true, 0), Object.class, getClass().getClassLoader()));
    }
    @Test public void nestedOptionalAndListBindingsAreResolvedWithoutAnEntitySample() {
        RuleBindingPlan.Binding b = new RuleBindingPlan.Binding("v", "field", Nested.class.getName() + ":child.names.1", String.class, true, 0);
        ForgeRuleBindings.Reader reader = ForgeRuleBindings.field(b, Nested.class, getClass().getClassLoader());
        Nested nested = new Nested(); assertEquals("b", reader.read(nested));
        nested.child = Optional.empty(); assertNull(reader.read(nested));
    }
    @Test public void nbtMissingIsNullButMalformedOrWrongTypeIsAnError() {
        net.minecraft.nbt.CompoundNBT root = new net.minecraft.nbt.CompoundNBT();
        RuleBindingPlan.Binding b = new RuleBindingPlan.Binding("v", "nbt", "CreateData.Processing.Time", Integer.class, true, 0);
        ForgeRuleBindings.Reader reader = ForgeRuleBindings.nbt(b);
        assertNull(reader.read(root));
        root.putString("CreateData", "bad");
        assertThrows(IllegalStateException.class, () -> reader.read(root));
        net.minecraft.nbt.CompoundNBT create = new net.minecraft.nbt.CompoundNBT();
        net.minecraft.nbt.CompoundNBT processing = new net.minecraft.nbt.CompoundNBT();
        processing.putInt("Time", 4); create.put("Processing", processing); root.put("CreateData", create);
        assertEquals(4, reader.read(root));
        processing.putString("Time", "4");
        assertThrows(IllegalStateException.class, () -> reader.read(root));
        assertThrows(RuleDiagnostic.class, () -> ForgeRuleBindings.nbt(new RuleBindingPlan.Binding("v", "nbt", "a..b", String.class, true, 0)));
        root.putInt("mod:processing", 7);
        assertEquals(7, ForgeRuleBindings.nbt(new RuleBindingPlan.Binding("v", "nbt", "mod:processing", Integer.class, true, 0)).read(root));
    }
    @Test public void compilesFieldsInTheColdPathAndReportsMissingOrWrongFields() {
        RuleBindingPlan.Binding binding = new RuleBindingPlan.Binding("v", "field", Holder.class.getName() + ":count", Integer.class, false, 12);
        ForgeRuleBindings.Reader reader = ForgeRuleBindings.field(binding, Holder.class, getClass().getClassLoader());
        assertEquals(7, reader.read(new Holder()));
        assertThrows(IllegalStateException.class, () -> reader.read(new Other()));
        assertThrows(RuleDiagnostic.class, () -> ForgeRuleBindings.field(new RuleBindingPlan.Binding("v", "field",
                Holder.class.getName() + ":missing", Integer.class, true, 3), Holder.class, getClass().getClassLoader()));
        assertThrows(RuleDiagnostic.class, () -> ForgeRuleBindings.field(new RuleBindingPlan.Binding("v", "field",
                Holder.class.getName() + ":count", String.class, false, 3), Holder.class, getClass().getClassLoader()));
    }
    @Test public void planChangesResetCachedSlotsAndLayoutsAreImmutable() {
        RuleBindingPlan known = RuleBindingPlan.defaults().withBinding("nbt", "one", Integer.class, true)
                .withBinding("nbt", "two", Integer.class, true);
        RuleBindingPlan first = LegacyRuleMigrationTest.source("v = [one] -> v != null && v > 0", known).bindings();
        RuleBindingPlan second = LegacyRuleMigrationTest.source("v = [two] -> v != null && v > 0", known).bindings();
        LegacyRuleMigrationTest.CountingContext context = new LegacyRuleMigrationTest.CountingContext();
        Map<String,Object> data = new HashMap<>(); data.put("one", 1); data.put("two", 2);
        context.reset(data, first); assertEquals(Integer.valueOf(1), context.value(0, Integer.class));
        context.reset(data, second); assertEquals(Integer.valueOf(2), context.value(0, Integer.class));
        assertThrows(UnsupportedOperationException.class, () -> second.slots().clear());
    }
}
