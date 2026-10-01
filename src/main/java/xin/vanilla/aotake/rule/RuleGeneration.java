package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.NativeMembers;
import xin.vanilla.aotake.api.sweep.SweepPolicy;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.util.EntityRulePolicy;

import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Lists, modes and executable rules share one owner-thread lifetime. */
public final class RuleGeneration<S> {
    public enum RuleList { SWEEP, CHUNK, REDLIST, CAPTURE }

    private final long id;
    private final Thread owner;
    private final Selection selection;
    private final Map<RuleList,List<BoundRule<S>>> rules = new EnumMap<>(RuleList.class);
    private String unavailable;
    private SweepPolicyCatalog.Prepared policies;
    private final Set<PolicyLease> leases = Collections.newSetFromMap(new IdentityHashMap<>());

    RuleGeneration(long id, Selection selection, RuleCatalog catalog, Map<String,EntityRule> compiled,
                   Function<RuleBindingPlan,LazyRuleContext<S>> contexts) {
        this(id, selection, catalog, compiled, (plan, members) -> contexts.apply(plan), null, Collections.emptyMap());
    }

    RuleGeneration(long id, Selection selection, RuleCatalog catalog, Map<String,EntityRule> compiled,
                   BiFunction<RuleBindingPlan,NativeMembers,LazyRuleContext<S>> contexts, SweepPolicyCatalog.Prepared policies,
                   Map<String,NativeMembers> ruleMembers) {
        this.id = id;
        this.owner = Thread.currentThread();
        this.selection = selection;
        this.policies = policies;
        if (policies != null) policies.requireId(selection.policyId);
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
                        NativeMembers members = reference.startsWith("script:") ? ruleMembers.getOrDefault(reference.substring(7), NativeMembers.EMPTY) : NativeMembers.EMPTY;
                        binding = new BoundRule<>(reference, rule, plan, Objects.requireNonNull(contexts.apply(plan, members)));
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

    PolicyLease lease(ConfigEditSnapshot configuration, Consumer<String> cancel) {
        requireUsable();
        if (policies == null) throw new IllegalStateException("Generation has no prepared policy catalog");
        PolicyLease lease = new PolicyLease(configuration, cancel); leases.add(lease); return lease;
    }

    /** The caller closes this lease only after task onFinish and persistence finalization. */
    public final class PolicyLease implements AutoCloseable {
        private ConfigEditSnapshot configuration;
        private Consumer<String> cancel;
        private SweepPolicy<?> instance;
        private boolean closed;
        private PolicyLease(ConfigEditSnapshot configuration, Consumer<String> cancel) {
            this.configuration = Objects.requireNonNull(configuration); this.cancel = Objects.requireNonNull(cancel);
        }
        private void check() { checkOwner(); if (closed) throw new IllegalStateException("Policy lease closed"); }
        public long generationId() { check(); return id; }
        public String policyId() { check(); return selection.policyId; }
        public ConfigEditSnapshot configuration() { check(); return configuration; }
        public NativeMembers members() { check(); return policies.members(selection.policyId); }
        public xin.vanilla.aotake.sweep.SweepExecutionPlan executionPlan() { check(); return policies.executionPlan(selection.policyId); }
        public SweepPolicy<?> policy() {
            check();
            if (instance == null) {
                requireUsable();
                try {
                    SweepPolicy<?> created = policies.create(selection.policyId);
                    check();
                    requireUsable();
                    instance = created;
                }
                catch (RuntimeException | Error failure) {
                    try { retire("Policy construction failed: " + failure.getMessage()); }
                    finally { close(); }
                    throw failure;
                }
            }
            return instance;
        }
        @Override public void close() {
            checkOwner(); if (closed) return;
            closed = true; instance = null; configuration = null; cancel = null;
            leases.remove(this); releaseRetired();
        }
    }

    public boolean matches(RuleList list, S subject) {
        requireUsable();
        Objects.requireNonNull(list);
        Objects.requireNonNull(subject);
        for (BoundRule<S> binding : rules.get(list)) {
            try {
                boolean matched = binding.context.evaluate(binding.rule, subject, binding.plan);
                // A reentrant rule may retire this generation and then swallow its exception.
                requireUsable();
                if (matched) return true;
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
    boolean hasSelection(ConfigHolder holder) { return selection.matches(holder); }

    void retire(String reason) {
        checkOwner();
        if (unavailable != null) return;
        unavailable = reason;
        for (PolicyLease lease : new ArrayList<>(leases)) {
            if (lease.closed) continue;
            try { lease.cancel.accept(reason); }
            catch (RuntimeException | Error error) {
                lease.close();
                if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
                if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                org.apache.logging.log4j.LogManager.getLogger().error("Policy cancellation callback failed in generation {}", id, error);
            }
        }
        releaseRetired();
    }

    private void releaseRetired() {
        if (unavailable == null || !leases.isEmpty()) return;
        for (List<BoundRule<S>> list : rules.values()) for (BoundRule<S> binding : list) binding.context.clear();
        rules.clear();
        policies = null;
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
        final String policyId;

        Selection(Map<String,Object> values) {
            Object policy = values.getOrDefault(SweepPolicyCatalog.CONFIG_PATH, SweepPolicyCatalog.DEFAULT_ID);
            if (!(policy instanceof String)) throw new IllegalArgumentException("Expected sweep policy ID");
            policyId = (String) policy;
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

        boolean matches(ConfigHolder holder) {
            // Keep checking live edits at every boundary without rebuilding the immutable snapshot.
            synchronized (holder) {
                if (!holder.matchesStoredValue(SweepPolicyCatalog.CONFIG_PATH, policyId)) return false;
                for (int i = 0; i < lists.size(); i++) {
                    if (!holder.matchesStoredValue(RuleMigrationTransaction.LISTS.get(i), lists.get(i))) return false;
                }
                for (int i = 0; i < modes.size(); i++) {
                    String path = RuleMigrationTransaction.MODES.get(i);
                    if (!holder.matchesStoredValue(path, modes.get(i))
                            && !holder.matchesStoredValue(path, modes.get(i).name())) return false;
                }
                return true;
            }
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return lists.equals(that.lists) && modes.equals(that.modes) && policyId.equals(that.policyId);
        }
        @Override public int hashCode() { return Objects.hash(lists, modes, policyId); }
    }
}
