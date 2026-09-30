package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import java.util.List;

/** Engine-owned services. Policies never receive this execution or persistence interface. */
public interface SweepBackend {
    long generationId();
    void requireUsable();
    ServerAccess server(Runnable callbackGuard, Runnable entityGuard);
    NativeMembers members();
    ConfigEditSnapshot configuration();
    List<EntityAccess> collect(SweepRequest request, SweepScope scope, Runnable guard);
    /** Canonicalize multipart entities, validate authorization and return a task-guarded view, or null. */
    EntityAccess normalize(EntityAccess entity, SweepScope scope, Runnable guard);
    Object identity(EntityAccess entity);
    /** Capture default safety/list data during this entity's evaluation, before afterEntity. */
    DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest request, EntityAccess entity);
    /** Only final DEFAULT decisions are included, with their captured scan data. */
    List<EntityAccess> filter(SweepRequest request, List<DefaultSweepFilter.Entry<EntityAccess, Object>> entries);
    List<EntityAccess> preflight(SweepRequest request, SweepScope scope, List<EntityAccess> selected);
    default List<List<EntityAccess>> batches(SweepRequest request, List<EntityAccess> selected) {
        return xin.vanilla.banira.common.util.CollectionUtils.splitToCollections(selected, request.batchSize());
    }
    /** Record each committed effect before moving on to any operation that can throw. */
    void execute(BatchContext context, Progress progress);
    void flush();
    void release();
    void fault(Throwable failure);

    final class Progress {
        private final int size;
        private int processed, skipped, failed, committed, pendingRemoval;
        private boolean pendingPersistence;
        public Progress(int size) { if (size < 0) throw new IllegalArgumentException("size"); this.size = size; }
        public int processed() { return processed; }
        public int accounted() { return processed + skipped + failed; }
        public void processed(int count, int committed, int pendingRemoval, boolean pendingPersistence) {
            check(count);
            if (committed < 0 || pendingRemoval < 0) throw new IllegalArgumentException("Negative commit count");
            this.processed = Math.addExact(this.processed, count);
            this.committed = Math.addExact(this.committed, committed);
            this.pendingRemoval = Math.addExact(this.pendingRemoval, pendingRemoval);
            this.pendingPersistence |= pendingPersistence;
        }
        public void skipped(int count) { check(count); skipped += count; }
        public void failed(int count) { check(count); failed += count; }
        private void check(int count) {
            if (count < 0 || (long) processed + skipped + failed + count > size) throw new IllegalArgumentException("Batch over-accounted");
        }
        public BatchResult snapshot(boolean cancelled, String failure) {
            return new BatchResult(size, processed, skipped, failed, committed, pendingRemoval, pendingPersistence, cancelled, failure);
        }
    }
}
