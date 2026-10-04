package xin.vanilla.aotake.rule;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuleReloadCoordinatorTest {
    @Test public void coalescesBurstAndDiscardsAnObsoleteResultOnTheOwnerThread() {
        Queue<Runnable> io = new ArrayDeque<>(), owner = new ArrayDeque<>();
        List<CompletableFuture<RuleMigrationTransaction.PreparedMigration>> jobs = new ArrayList<>();
        RuleReloadCoordinator<RuleMigrationTransaction.PreparedMigration> coordinator = new RuleReloadCoordinator<>(io::add, owner::add, () -> {
            CompletableFuture<RuleMigrationTransaction.PreparedMigration> job = new CompletableFuture<>(); jobs.add(job); return job;
        });
        CompletableFuture<RuleMigrationTransaction.PreparedMigration> result = coordinator.requestReload();
        for (int i = 0; i < 100; i++) assertSame(result, coordinator.requestReload());
        assertEquals(1, io.size()); io.remove().run(); assertEquals(1, jobs.size());
        assertSame(result, coordinator.requestReload());
        jobs.get(0).complete(candidate("old")); assertFalse(result.isDone()); owner.remove().run();
        assertFalse(result.isDone()); assertEquals(1, io.size()); io.remove().run();
        jobs.get(1).complete(candidate("new")); assertFalse(result.isDone()); owner.remove().run();
        assertEquals("new", result.join().id()); assertTrue(io.isEmpty()); assertTrue(owner.isEmpty());
        coordinator.close();
    }
    @Test public void closeAndExecutorRejectionReleasePendingRequests() {
        Queue<Runnable> io = new ArrayDeque<>(), owner = new ArrayDeque<>(); AtomicInteger calls = new AtomicInteger();
        RuleReloadCoordinator<Object> coordinator = new RuleReloadCoordinator<>(io::add, owner::add, () -> { calls.incrementAndGet(); return new CompletableFuture<>(); });
        CompletableFuture<?> pending = coordinator.requestReload(); coordinator.close(); io.remove().run();
        assertTrue(pending.isCompletedExceptionally()); assertEquals(0, calls.get());
        assertTrue(coordinator.requestReload().isCompletedExceptionally());
        RuleReloadCoordinator<Object> rejected = new RuleReloadCoordinator<>(task -> { throw new RejectedExecutionException("stopped"); }, Runnable::run, () -> new CompletableFuture<>());
        assertTrue(rejected.requestReload().isCompletedExceptionally()); rejected.close();
    }
    @Test public void preparationFailureReachesCallerAndNextRequestCanRetry() {
        RuleReloadCoordinator<Object> coordinator = new RuleReloadCoordinator<>(Runnable::run, Runnable::run, () -> { throw new IllegalStateException("read failed"); });
        CompletableFuture<?> first = coordinator.requestReload();
        assertTrue(first.isCompletedExceptionally()); assertTrue(coordinator.requestReload().isCompletedExceptionally()); coordinator.close();
    }
    private static RuleMigrationTransaction.PreparedMigration candidate(String id) {
        return new RuleMigrationTransaction.PreparedMigration(null, 0, id, null, Collections.emptyMap(), null, null, false);
    }
}
