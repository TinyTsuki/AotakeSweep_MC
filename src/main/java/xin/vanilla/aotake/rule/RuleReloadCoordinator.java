package xin.vanilla.aotake.rule;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * One pending batch, at most one preparation in flight; completion is dispatched to the owner.
 */
public final class RuleReloadCoordinator<T> implements AutoCloseable {
    private final Executor worker, owner;
    private final Supplier<CompletableFuture<T>> prepare;
    private CompletableFuture<T> pending;
    private long requested;
    private boolean closed;

    public RuleReloadCoordinator(Executor worker, Executor owner, Supplier<CompletableFuture<T>> prepare) {
        this.worker = Objects.requireNonNull(worker);
        this.owner = Objects.requireNonNull(owner);
        this.prepare = Objects.requireNonNull(prepare);
    }

    public CompletableFuture<T> requestReload() {
        CompletableFuture<T> result;
        boolean start;
        synchronized (this) {
            if (closed) {
                result = new CompletableFuture<>();
                result.completeExceptionally(new CancellationException("Rule reload closed"));
                return result;
            }
            requested++;
            start = pending == null;
            if (start) pending = new CompletableFuture<>();
            result = pending;
        }
        if (start) schedule();
        return result;
    }

    private void schedule() {
        try {
            worker.execute(this::run);
        } catch (RuntimeException error) {
            reject(error);
        }
    }

    private void run() {
        long ticket;
        synchronized (this) {
            if (closed || pending == null) return;
            ticket = requested;
        }
        try {
            Objects.requireNonNull(prepare.get(), "Preparation future").whenComplete((candidate, error) -> {
                try {
                    owner.execute(() -> complete(ticket, candidate, error));
                } catch (RuntimeException rejected) {
                    reject(rejected);
                }
            });
        } catch (Exception error) {
            try {
                owner.execute(() -> complete(ticket, null, error));
            } catch (RuntimeException rejected) {
                reject(rejected);
            }
        }
    }

    private void complete(long ticket, T candidate, Throwable error) {
        CompletableFuture<T> result;
        boolean retry;
        synchronized (this) {
            if (closed || pending == null) return;
            retry = ticket != requested;
            result = pending;
            if (!retry) pending = null;
        }
        if (retry) schedule();
        else if (error != null) result.completeExceptionally(error);
        else result.complete(candidate);
    }

    private void reject(Throwable error) {
        CompletableFuture<T> result;
        synchronized (this) {
            result = pending;
            pending = null;
        }
        if (result != null) result.completeExceptionally(error);
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            requested++;
        }
        reject(new CancellationException("Rule reload closed"));
    }
}
