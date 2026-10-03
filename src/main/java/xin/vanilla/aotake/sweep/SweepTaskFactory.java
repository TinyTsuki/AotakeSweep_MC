package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.sweep.SweepPolicy;
import xin.vanilla.aotake.api.sweep.SweepRequest;
import xin.vanilla.aotake.rule.RuleGeneration;
import xin.vanilla.aotake.rule.RuleManager;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Binds one task instance to one execution generation until all finalization has finished.
 */
public final class SweepTaskFactory {
    private SweepTaskFactory() {
    }

    public static <E> SweepTask<?> create(UUID id, SweepRequest request, RuleManager<E> manager,
                                          Function<RuleGeneration<E>.PolicyLease, SweepBackend> backendFactory) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(request);
        Objects.requireNonNull(manager);
        Objects.requireNonNull(backendFactory);
        AtomicReference<SweepTask<?>> task = new AtomicReference<>();
        RuleGeneration<E>.PolicyLease lease = manager.leasePolicy(reason -> {
            SweepTask<?> current = task.get();
            if (current != null) current.cancel(reason);
        });
        SweepBackend backend = null;
        try {
            SweepPolicy<?> policy = lease.policy();
            backend = Objects.requireNonNull(backendFactory.apply(lease), "backend");
            if (backend.generationId() != lease.generationId() || !manager.isUsable(lease.generationId()))
                throw new IllegalStateException("Sweep backend belongs to an unavailable generation");
            SweepTask<?> created = new SweepTask<>(id, request, backend, policy, lease.executionPlan(), lease::close);
            task.set(created);
            return created;
        } catch (RuntimeException | Error failure) {
            try {
                if (backend != null) {
                    try {
                        backend.release();
                    } catch (RuntimeException | Error releaseFailure) {
                        SweepTask.rethrowFatal(releaseFailure);
                        if (releaseFailure != failure) failure.addSuppressed(releaseFailure);
                    }
                }
            } finally {
                lease.close();
            }
            throw failure;
        }
    }
}
