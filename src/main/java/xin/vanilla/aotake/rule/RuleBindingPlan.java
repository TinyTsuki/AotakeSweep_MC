package xin.vanilla.aotake.rule;

import java.util.*;

/**
 * Immutable type declarations and resolved slot layout; never holds entities.
 */
public final class RuleBindingPlan {
    public static final String CREATE_TIME = "CreateData.Processing.Time";
    private final Map<String, Binding> known;
    private final List<Binding> slots;

    public RuleBindingPlan(Map<String, Binding> known, List<Binding> slots) {
        this.known = Collections.unmodifiableMap(new LinkedHashMap<>(known));
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public static RuleBindingPlan defaults() {
        return new RuleBindingPlan(Collections.emptyMap(), Collections.emptyList())
                .withBinding("nbt", CREATE_TIME, Integer.class, true)
                .withBinding("field", "entity.tick", Integer.class, false)
                .withBinding("field", "tickCount", Integer.class, false)
                .withBinding("synced", "living.health", Float.class, true);
    }

    public RuleBindingPlan withBinding(String kind, String payload, Class<?> type, boolean nullable) {
        Map<String, Binding> next = new LinkedHashMap<>(known);
        next.put(key(kind, payload), new Binding("", kind, payload, type, nullable, -1));
        return new RuleBindingPlan(next, slots);
    }

    public RuleBindingPlan resolved(List<Binding> bindings) {
        return new RuleBindingPlan(known, bindings);
    }

    public Binding known(String kind, String payload) {
        return known.get(key(kind, payload));
    }

    public List<Binding> slots() {
        return slots;
    }

    public Binding named(String name) {
        for (Binding binding : slots) if (binding.name.equals(name)) return binding;
        return null;
    }

    private static String key(String kind, String payload) {
        return kind + "\u0000" + payload;
    }

    public static final class Binding {
        private final String name, kind, payload;
        private final Class<?> type;
        private final boolean nullable;
        private final int offset;

        public Binding(String name, String kind, String payload, Class<?> type, boolean nullable, int offset) {
            this.name = Objects.requireNonNull(name);
            this.kind = Objects.requireNonNull(kind);
            this.payload = Objects.requireNonNull(payload);
            this.type = Objects.requireNonNull(type);
            this.nullable = nullable;
            this.offset = offset;
            if (type.isPrimitive() || type.getCanonicalName() == null)
                throw new IllegalArgumentException("Binding type must be a named reference type");
        }

        public String name() {
            return name;
        }

        public String kind() {
            return kind;
        }

        public String payload() {
            return payload;
        }

        public Class<?> type() {
            return type;
        }

        public boolean nullable() {
            return nullable;
        }

        public int offset() {
            return offset;
        }
    }
}
