package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.util.EntityRulePolicy;

import java.util.*;
import java.util.function.Function;

/** Lists, modes and executable rules share one owner-thread lifetime. */
public final class RuleGeneration<S> {
    public enum RuleList { SWEEP, CHUNK, REDLIST, CAPTURE }

    private final long id;
    private final Thread owner;
    private final Selection selection;
    private final Map<RuleList,List<BoundRule<S>>> rules = new EnumMap<>(RuleList.class);
    private String unavailable;

    RuleGeneration(long id, Selection selection, RuleCatalog catalog, Map<String,EntityRule> compiled,
                   Function<RuleBindingPlan,LazyRuleContext<S>> contexts) {
        this.id = id;
        this.owner = Thread.currentThread();
        this.selection = selection;
        Map<String,BoundRule<S>> shared = new HashMap<>();
        try {
            for (RuleList list : RuleList.values()) {
                List<String> references = selection.lists.get(list.ordinal());
                catalog.validateReferences(references);
                List<BoundRule<S>> bindings = new ArrayList<>();
                for (String reference : references) {
                    BoundRule<S> binding = shared.get(reference);
                    if (binding == null) {
                        EntityRule rule;
                        RuleBindingPlan plan;
                        if (reference.startsWith("script:")) {
                            String scriptId = reference.substring(7);
                            rule = Objects.requireNonNull(compiled.get(scriptId), "Compiled rule missing: " + scriptId);
                            plan = catalog.sources().get(scriptId).bindings();
                        } else {
                            int colon = reference.indexOf(':');
                            String namespace = reference.substring(0, colon), path = reference.substring(colon + 1);
                            rule = context -> {
                                String resource = context.resourceId();
                                int separator = resource.indexOf(':');
                                if (separator < 1) throw new IllegalStateException("Invalid entity resource: " + resource);
                                return (namespace.equals("*") || resource.regionMatches(0, namespace, 0, namespace.length()) && separator == namespace.length())
                                        && (path.equals("*") || resource.substring(separator + 1).equals(path));
                            };
                            plan = RuleBindingPlan.defaults();
                        }
                        binding = new BoundRule<>(reference, rule, plan, Objects.requireNonNull(contexts.apply(plan)));
                        shared.put(reference, binding);
                    }
                    bindings.add(binding);
                }
                rules.put(list, Collections.unmodifiableList(bindings));
            }
        } catch (RuntimeException | Error failure) {
            for (BoundRule<S> binding : shared.values()) binding.context.clear();
            rules.clear();
            throw failure;
        }
    }

    public long id() { return id; }

    public boolean matches(RuleList list, S subject) {
        requireUsable();
        Objects.requireNonNull(list);
        Objects.requireNonNull(subject);
        for (BoundRule<S> binding : rules.get(list)) {
            try {
                if (binding.context.evaluate(binding.rule, subject, binding.plan)) return true;
            } catch (RuntimeException | Error failure) {
                String message = "Rule " + binding.reference + " failed in generation " + id;
                retire(message);
                if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
                if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
                throw new RuleExecutionException(id, message, failure);
            }
        }
        return false;
    }

    public boolean shouldClean(boolean chunk, S subject) {
        requireUsable();
        int index = chunk ? 1 : 0;
        boolean empty = selection.lists.get(index).isEmpty();
        boolean matched = !empty && matches(chunk ? RuleList.CHUNK : RuleList.SWEEP, subject);
        return EntityRulePolicy.shouldClean(empty, selection.modes.get(index), matched);
    }

    public void requireUsable() {
        checkOwner();
        if (unavailable != null) throw new RuleExecutionException(id, unavailable);
    }

    boolean isUsable() { checkOwner(); return unavailable == null; }
    boolean hasSelection(Selection expected) { return selection.equals(expected); }

    void retire(String reason) {
        checkOwner();
        if (unavailable != null) return;
        unavailable = reason;
        for (List<BoundRule<S>> list : rules.values()) for (BoundRule<S> binding : list) binding.context.clear();
        rules.clear();
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Entity rules must run on their owner thread");
    }

    private static final class BoundRule<S> {
        final String reference;
        final EntityRule rule;
        final RuleBindingPlan plan;
        final LazyRuleContext<S> context;
        BoundRule(String reference, EntityRule rule, RuleBindingPlan plan, LazyRuleContext<S> context) {
            this.reference = reference; this.rule = rule; this.plan = plan; this.context = context;
        }
    }

    static final class Selection {
        final List<List<String>> lists;
        final List<EnumListType> modes;

        Selection(Map<String,Object> values) {
            List<List<String>> lists = new ArrayList<>();
            for (String path : RuleMigrationTransaction.LISTS) {
                Object value = values.get(path);
                if (!(value instanceof List)) throw new IllegalArgumentException("Expected rule list: " + path);
                List<String> copy = new ArrayList<>();
                for (Object rule : (List<?>) value) {
                    if (!(rule instanceof String)) throw new IllegalArgumentException("Expected string rule: " + path);
                    copy.add((String) rule);
                }
                lists.add(Collections.unmodifiableList(copy));
            }
            this.lists = Collections.unmodifiableList(lists);
            List<EnumListType> modes = new ArrayList<>();
            for (String path : RuleMigrationTransaction.MODES) {
                Object mode = values.get(path);
                modes.add(mode instanceof EnumListType ? (EnumListType) mode : EnumListType.valueOf(String.valueOf(mode)));
            }
            this.modes = Collections.unmodifiableList(modes);
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return lists.equals(that.lists) && modes.equals(that.modes);
        }
        @Override public int hashCode() { return Objects.hash(lists, modes); }
    }
}
