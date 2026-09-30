package xin.vanilla.aotake.api.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import java.util.*;

public final class SweepRequest {
    private final SweepTrigger trigger;
    private final SweepOperation operation;
    private final SweepScope scope;
    private final boolean ignoreFilter, preFiltered;
    private final int batchSize;
    private final List<EntityAccess> candidates;

    private SweepRequest(Builder b) {
        trigger = b.trigger; operation = b.operation; scope = b.scope;
        ignoreFilter = b.ignoreFilter; preFiltered = b.preFiltered; batchSize = b.batchSize;
        candidates = b.candidates == null ? null : Collections.unmodifiableList(new ArrayList<>(b.candidates));
    }
    public SweepTrigger trigger() { return trigger; }
    public SweepOperation operation() { return operation; }
    public SweepScope scope() { return scope; }
    public boolean ignoreFilter() { return ignoreFilter; }
    public boolean preFiltered() { return preFiltered; }
    public int batchSize() { return batchSize; }
    /** Null means collect from the authorized scope. An empty list remains empty. */
    public List<EntityAccess> candidates() { return candidates; }
    public static Builder builder(SweepTrigger trigger, SweepOperation operation) { return new Builder(trigger, operation); }

    public static final class Builder {
        private final SweepTrigger trigger;
        private final SweepOperation operation;
        private SweepScope scope = SweepScope.all();
        private boolean ignoreFilter, preFiltered;
        private int batchSize = 256;
        private List<EntityAccess> candidates;
        private Builder(SweepTrigger trigger, SweepOperation operation) {
            this.trigger = Objects.requireNonNull(trigger, "trigger"); this.operation = Objects.requireNonNull(operation, "operation");
        }
        public Builder scope(SweepScope value) { scope = Objects.requireNonNull(value, "scope"); return this; }
        public Builder ignoreFilter(boolean value) { ignoreFilter = value; return this; }
        public Builder preFiltered(boolean value) { preFiltered = value; return this; }
        public Builder batchSize(int value) { if (value <= 0) throw new IllegalArgumentException("batchSize must be positive"); batchSize = value; return this; }
        public Builder candidates(List<EntityAccess> value) { candidates = Objects.requireNonNull(value, "candidates"); return this; }
        public SweepRequest build() { return new SweepRequest(this); }
    }
}
