package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import java.util.function.Function;

/** Task-owned identity index; public evaluations are stable snapshots created only on demand. */
final class SweepCandidates {
    static final class Record {
        final EntityAccess entity;
        boolean skipped, chosen;
        private boolean startAllowed, startEmitted;
        EntityDisposition disposition = EntityDisposition.DEFAULT;
        DefaultSweepFilter.Entry<EntityAccess,Object> scan;
        private EntityEvaluation snapshot;
        private long selectedAt;
        Record(EntityAccess entity) { this.entity = entity; }
        void update(boolean skipped, EntityDisposition disposition) {
            this.skipped = skipped; this.disposition = Objects.requireNonNull(disposition); snapshot = null;
            if (skipped || disposition != EntityDisposition.DEFAULT) scan = null;
        }
        EntityEvaluation evaluation() {
            if (snapshot == null) snapshot = skipped ? EntityEvaluation.skipped(entity) : EntityEvaluation.of(entity, disposition);
            return snapshot;
        }
    }
    private final Function<EntityAccess,Object> identity;
    private IdentityHashMap<Object,Record> index = new IdentityHashMap<>();
    private final ArrayList<Record> ordered = new ArrayList<>();
    private long selection;
    SweepCandidates(Function<EntityAccess,Object> identity) { this.identity = identity; }
    void reserve(int expected) {
        if (expected < 0) throw new IllegalArgumentException("negative candidate count");
        if (index.isEmpty() && expected > 32) index = new IdentityHashMap<>(expected);
        ordered.ensureCapacity(expected);
    }
    void add(EntityAccess entity) {
        Object key = Objects.requireNonNull(identity.apply(entity), "identity");
        if (!index.containsKey(key)) { Record r = new Record(entity); index.put(key, r); ordered.add(r); }
    }
    List<Record> records() { return ordered; }
    Record get(EntityAccess entity) { return index.get(identity.apply(Objects.requireNonNull(entity, "entity"))); }
    List<EntityAccess> entities() {
        List<EntityAccess> result = new ArrayList<>(ordered.size());
        for (Record r : ordered) result.add(r.entity);
        return Collections.unmodifiableList(result);
    }
    List<EntityEvaluation> evaluations() {
        List<EntityEvaluation> result = new ArrayList<>(ordered.size());
        for (Record r : ordered) result.add(r.evaluation());
        return Collections.unmodifiableList(result);
    }
    List<EntityAccess> restrict(List<EntityAccess> choice) {
        if (++selection == 0) { for (Record r : ordered) r.selectedAt = 0; selection = 1; }
        List<EntityAccess> result = new ArrayList<>(choice.size());
        for (EntityAccess entity : choice) {
            Record r = get(entity);
            if (r == null || r.skipped) throw new IllegalArgumentException("Selected entity was not evaluated or was skipped");
            if (r.selectedAt != selection) { r.selectedAt = selection; result.add(r.entity); }
        }
        return result;
    }
    void authorizeStart(List<EntityAccess> selected) {
        selected.forEach(entity -> {
            Record r = get(entity);
            if (r == null || r.skipped) throw new IllegalArgumentException("Selected entity was not evaluated or was skipped");
            r.startAllowed = true;
        });
    }
    EntityAccess consumeStart(EntityAccess entity) {
        Record r = get(entity);
        if (r == null || !r.startAllowed) throw new IllegalArgumentException("Selected entity was not evaluated or was skipped");
        if (r.startEmitted) return null;
        r.startEmitted = true;
        return r.entity;
    }
    List<EntityAccess> filterDefault(boolean bypass, Function<List<DefaultSweepFilter.Entry<EntityAccess,Object>>,List<EntityAccess>> filter) {
        List<DefaultSweepFilter.Entry<EntityAccess,Object>> scans = new ArrayList<>();
        ordered.forEach(r -> {
            r.chosen = false;
            if (!r.skipped && r.disposition == EntityDisposition.DEFAULT && !bypass)
                scans.add(Objects.requireNonNull(r.scan, "missing default scan"));
        });
        if (!bypass) filter.apply(scans).forEach(entity -> {
            Record r = get(entity);
            if (r == null) throw new IllegalArgumentException("Default filter selected unknown entity");
            r.chosen = true;
        });
        List<EntityAccess> result = new ArrayList<>();
        ordered.forEach(r -> {
            if (!r.skipped && (r.disposition == EntityDisposition.CLEAN
                    || r.disposition == EntityDisposition.DEFAULT && (bypass || r.chosen))) result.add(r.entity);
        });
        return result;
    }
    void clear() { ordered.clear(); index.clear(); }
}
