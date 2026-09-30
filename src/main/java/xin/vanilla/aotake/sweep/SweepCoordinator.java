package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.sweep.SweepRequest;
import java.util.*;
import java.util.function.*;

/** Reentrant submissions are always queued; a tick drains only its initial queue length. */
public final class SweepCoordinator {
    private final Thread owner = Thread.currentThread();
    private final BiFunction<UUID, SweepRequest, SweepTask<?>> factory;
    private final Consumer<PreparedSweep> ready;
    private final BiConsumer<UUID, Throwable> failures;
    private final Consumer<SweepTask<?>> completed;
    private final Deque<Pending> pending = new ArrayDeque<>();
    private final Map<UUID, SweepTask<?>> active = new LinkedHashMap<>();
    private boolean stopped, draining;
    public SweepCoordinator(BiFunction<UUID, SweepRequest, SweepTask<?>> factory, Consumer<PreparedSweep> ready, BiConsumer<UUID, Throwable> failures) {
        this(factory, ready, failures, task -> {});
    }
    public SweepCoordinator(BiFunction<UUID, SweepRequest, SweepTask<?>> factory, Consumer<PreparedSweep> ready,
                            BiConsumer<UUID, Throwable> failures, Consumer<SweepTask<?>> completed) {
        this.factory = Objects.requireNonNull(factory); this.ready = Objects.requireNonNull(ready); this.failures = Objects.requireNonNull(failures);
        this.completed = Objects.requireNonNull(completed);
    }
    public UUID submit(SweepRequest request) {
        checkOwner(); if (stopped) throw new IllegalStateException("Sweep coordinator stopped");
        UUID id = UUID.randomUUID(); pending.addLast(new Pending(id, Objects.requireNonNull(request))); return id;
    }
    public void tick() {
        checkOwner(); if (draining) throw new IllegalStateException("Recursive queue draining");
        if (stopped) return;
        draining = true;
        try {
            int limit = pending.size();
            for (int i = 0; i < limit && !stopped && !pending.isEmpty(); i++) {
                Pending next = pending.removeFirst();
                SweepTask<?> task = null;
                try {
                    task = Objects.requireNonNull(factory.apply(next.id, next.request), "task factory result");
                    if (!next.id.equals(task.id())) throw new IllegalStateException("Task identity mismatch");
                    final SweepTask<?> registered = task;
                    task.onCompletion(() -> {
                        active.remove(next.id, registered);
                        try { completed.accept(registered); }
                        catch (RuntimeException | Error error) {
                            SweepTask.rethrowFatal(error);
                            failures.accept(next.id, error);
                        }
                    });
                    active.put(next.id, task);
                    if (stopped) { task.cancel("server stopped"); continue; }
                    PreparedSweep prepared = task.prepare();
                    if (prepared != null) ready.accept(prepared);
                } catch (RuntimeException | Error error) {
                    SweepTask.rethrowFatal(error);
                    if (task != null) task.cancel("task dispatch failed");
                    failures.accept(next.id, error);
                }
            }
        } finally { draining = false; }
    }
    public void stop() {
        checkOwner(); if (stopped) return;
        stopped = true; pending.clear();
        RuntimeException failure = null;
        for (SweepTask<?> task : new ArrayList<>(active.values())) {
            try { task.cancel("server stopped"); }
            catch (RuntimeException | Error error) {
                SweepTask.rethrowFatal(error);
                if (failure == null) failure = new IllegalStateException("Sweep shutdown failed", error);
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
    public int queued() { checkOwner(); return pending.size(); }
    public int active() { checkOwner(); return active.size(); }
    private void checkOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Sweep queue requires owner thread"); }
    private static final class Pending {
        final UUID id;
        final SweepRequest request;
        Pending(UUID id, SweepRequest request) { this.id = id; this.request = request; }
    }
}
