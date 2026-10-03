package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.api.rule.EntityRuleContext;
import xin.vanilla.aotake.api.rule.EntityRule;
import java.util.*;

/** One reusable context per scan; reset and clear must run on the entity owner thread. */
public abstract class LazyRuleContext<S> implements EntityRuleContext {
    private static final RuleBindingPlan.Binding[] BUILTINS = {
            new RuleBindingPlan.Binding("resource", "builtin", "resource", String.class, false, 0),
            new RuleBindingPlan.Binding("tick", "builtin", "tick", Integer.class, false, 0),
            new RuleBindingPlan.Binding("customName", "builtin", "customName", String.class, true, 0),
            new RuleBindingPlan.Binding("hasOwner", "builtin", "hasOwner", Boolean.class, false, 0),
            new RuleBindingPlan.Binding("isItem", "builtin", "isItem", Boolean.class, false, 0)
    };
    private S subject;
    private RuleBindingPlan plan;
    private long frame, nextFrame;
    private Object[] values = new Object[0];
    private boolean[] loaded = new boolean[0];
    private Object[] builtinValues = new Object[BUILTINS.length];
    private boolean[] builtinLoaded = new boolean[BUILTINS.length];

    public final void reset(S subject, RuleBindingPlan plan) {
        bindPlan(plan);
        clear();
        this.subject = Objects.requireNonNull(subject);
        this.plan = Objects.requireNonNull(plan);
        frame = nextFrame = Math.incrementExact(nextFrame);
        if (values.length < plan.slots().size()) {
            values = new Object[plan.slots().size()]; loaded = new boolean[values.length];
        }
    }
    public void clear() {
        subject = null; plan = null; frame = 0;
        Arrays.fill(values, null); Arrays.fill(loaded, false);
        Arrays.fill(builtinValues, null); Arrays.fill(builtinLoaded, false);
    }
    protected final S subject() { return subject; }
    protected final long evaluationFrame() { return frame; }
    protected abstract Object read(RuleBindingPlan.Binding binding);
    protected void bindPlan(RuleBindingPlan plan) { }
    protected void checkAccess() { }

    public final boolean evaluate(EntityRule rule, S subject, RuleBindingPlan plan) {
        // Ordinary scans reuse arrays; only reentrant calls need another evaluation frame.
        S previousSubject = this.subject;
        RuleBindingPlan previousPlan = this.plan;
        long previousFrame = frame;
        Object[] previousValues = values, previousBuiltinValues = builtinValues;
        boolean[] previousLoaded = loaded, previousBuiltinLoaded = builtinLoaded;
        if (previousSubject != null) {
            checkAccess();
            values = new Object[0]; loaded = new boolean[0];
            builtinValues = new Object[BUILTINS.length]; builtinLoaded = new boolean[BUILTINS.length];
            this.subject = null; this.plan = null;
        }
        try {
            reset(subject, plan);
            return rule.test(this);
        } finally {
            clear();
            if (previousSubject != null) {
                this.subject = previousSubject; this.plan = previousPlan; frame = previousFrame;
                values = previousValues; loaded = previousLoaded;
                builtinValues = previousBuiltinValues; builtinLoaded = previousBuiltinLoaded;
            }
        }
    }

    public final boolean evaluateResource(EntityRule fallback, S subject, RuleBindingPlan plan, String namespace, String path) {
        bindPlan(plan);
        return evaluateResourceValue(fallback, Objects.requireNonNull(subject), plan, namespace, path);
    }

    /** Opt in only for keys compatible across contexts during a pure resource-rule run. */
    protected Class<?> resourceMatchKeyType() { return null; }

    final Object resourceMatchKey(S subject, RuleBindingPlan plan) {
        bindPlan(plan);
        return readResourceMatchKey(Objects.requireNonNull(subject));
    }

    final boolean evaluateResource(EntityRule fallback, S subject, RuleBindingPlan plan,
                                   String namespace, String path, Object key) {
        bindPlan(plan);
        return evaluateResourceValue(fallback, Objects.requireNonNull(subject), plan, namespace, path, key);
    }

    protected Object readResourceMatchKey(S subject) { return null; }

    protected boolean evaluateResourceValue(EntityRule fallback, S subject, RuleBindingPlan plan,
                                            String namespace, String path, Object key) {
        return evaluateResourceValue(fallback, subject, plan, namespace, path);
    }

    protected boolean evaluateResourceValue(EntityRule fallback, S subject, RuleBindingPlan plan, String namespace, String path) {
        return evaluate(fallback, subject, plan);
    }

    @Override public final <T> T value(int slot, Class<T> type) {
        checkAccess();
        if (plan == null) throw new IllegalStateException("Rule context is not bound to an entity");
        RuleBindingPlan.Binding binding = plan.slots().get(slot);
        if (binding.type() != type) throw new IllegalArgumentException("Wrong binding type for " + binding.name());
        if (!loaded[slot]) {
            Object value = read(binding);
            if (value == null ? !binding.nullable() : !type.isInstance(value)) {
                throw new IllegalStateException("Invalid value for binding " + binding.name() + ": expected " + type.getName());
            }
            values[slot] = value; loaded[slot] = true;
        }
        return type.cast(values[slot]);
    }
    private <T> T builtin(int index, Class<T> type) {
        checkAccess();
        if (plan == null) throw new IllegalStateException("Rule context is not bound to an entity");
        if (builtinLoaded[index]) return type.cast(builtinValues[index]);
        RuleBindingPlan.Binding binding = BUILTINS[index];
        for (int i = 0; i < plan.slots().size(); i++) {
            RuleBindingPlan.Binding b = plan.slots().get(i);
            if (b.kind().equals("builtin") && b.payload().equals(binding.payload())) {
                builtinValues[index] = value(i, type); builtinLoaded[index] = true;
                return type.cast(builtinValues[index]);
            }
        }
        Object value = read(binding);
        if (value == null ? !binding.nullable() : !type.isInstance(value)) throw new IllegalStateException("Invalid builtin value: " + binding.name());
        builtinValues[index] = value; builtinLoaded[index] = true;
        return type.cast(value);
    }
    @Override public String resourceId() { return builtin(0, String.class); }
    @Override public int tick() { return builtin(1, Integer.class); }
    @Override public String customName() { return builtin(2, String.class); }
    @Override public boolean hasOwner() { return builtin(3, Boolean.class); }
    @Override public boolean isItem() { return builtin(4, Boolean.class); }
}
