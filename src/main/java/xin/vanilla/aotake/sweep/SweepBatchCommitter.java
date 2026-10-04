package xin.vanilla.aotake.sweep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shared commit boundary for scheduled batches and policy-driven cleanup.
 */
public final class SweepBatchCommitter<E> {
    public interface Operations<E> {
        void requireUsable();

        void prepare(List<E> entities);

        void begin();

        boolean eligible(E entity);

        /**
         * Account for ownership before any later operation can throw.
         */
        void commit(E entity, SweepBackend.Progress progress);

        void flush();
    }

    private final Thread owner = Thread.currentThread();
    private final Operations<E> operations;
    private boolean begun, executing;

    public SweepBatchCommitter(Operations<E> operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    public void execute(List<E> entities, SweepBackend.Progress progress) {
        if (Thread.currentThread() != owner || executing)
            throw new IllegalStateException("Invalid batch execution context");
        List<E> batch = Collections.unmodifiableList(new ArrayList<>(entities));
        Objects.requireNonNull(progress);
        executing = true;
        Throwable failure = null;
        try {
            operations.requireUsable();
            operations.prepare(batch);
            operations.requireUsable();
            if (!begun) {
                begun = true;
                operations.begin();
            }
            for (E entity : batch) {
                operations.requireUsable();
                if (!operations.eligible(entity)) {
                    progress.skipped(1);
                    continue;
                }
                int before = progress.accounted();
                try {
                    operations.commit(entity, progress);
                    if (progress.accounted() != before + 1)
                        throw new IllegalStateException("Commit must account for exactly one entity");
                } catch (RuntimeException | Error error) {
                    if (progress.accounted() == before) progress.failed(1);
                    throw error;
                }
            }
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            try {
                operations.flush();
            } catch (RuntimeException | Error error) {
                SweepTask.rethrowFatal(error);
                if (failure == null) throw error;
                if (failure != error) failure.addSuppressed(error);
            } finally {
                executing = false;
            }
        }
    }
}
