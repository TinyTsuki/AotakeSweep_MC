package xin.vanilla.aotake.rule;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Complete the fallible rule phase before any inventory or entity mutation. */
public final class CleanupRuleGate<S> {
    private final RuleGeneration<S> generation;
    private final BooleanSupplier usable;

    public CleanupRuleGate(RuleGeneration<S> generation, BooleanSupplier usable) {
        this.generation = Objects.requireNonNull(generation);
        this.usable = Objects.requireNonNull(usable);
    }

    public void requireUsable() {
        generation.requireUsable();
        if (!usable.getAsBoolean()) throw new RuleExecutionException(generation.id(), "Cleanup rules changed before commit");
    }

    public List<Decision<S>> prepare(List<S> subjects, Predicate<S> isItem, boolean captureEnabled) {
        requireUsable();
        List<Decision<S>> decisions = new ArrayList<>(subjects.size());
        for (S subject : subjects) {
            boolean recycle = isItem.test(subject)
                    ? !generation.matches(RuleGeneration.RuleList.REDLIST, subject)
                    : captureEnabled && generation.matches(RuleGeneration.RuleList.CAPTURE, subject);
            decisions.add(new Decision<>(subject, recycle));
        }
        requireUsable();
        return decisions;
    }

    public static final class Decision<S> {
        private final S subject;
        private final boolean recycle;
        private Decision(S subject, boolean recycle) { this.subject = subject; this.recycle = recycle; }
        public S subject() { return subject; }
        public boolean recycle() { return recycle; }
    }
}
