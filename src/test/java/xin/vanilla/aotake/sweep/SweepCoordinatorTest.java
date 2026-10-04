package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class SweepCoordinatorTest {
    @Test public void reentrantSubmissionWaitsForNextTickAndRecursiveDrainingIsRejected() {
        SweepCoordinator[] c = new SweepCoordinator[1];
        SweepRequest request = SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.SCAN_ONLY).build();
        List<UUID> created = new ArrayList<>();
        c[0] = new SweepCoordinator((id, r) -> {
            created.add(id);
            return new SweepTask<>(id, r, new SweepFixture(), new DefaultSweepPolicy<Void>() {
                public Void createState(SweepContext context) { c[0].submit(request); assertThrows(IllegalStateException.class, c[0]::tick); return null; }
            });
        }, p -> fail("scan cannot be ready"), (id, error) -> { throw new AssertionError(error); });
        c[0].submit(request); c[0].tick(); assertEquals(1, created.size()); assertEquals(1, c[0].queued());
        c[0].tick(); assertEquals(2, created.size()); assertEquals(1, c[0].queued()); assertEquals(0, c[0].active());
        c[0].stop(); assertEquals(0, c[0].queued()); assertThrows(IllegalStateException.class, () -> c[0].submit(request));
    }

    @Test public void independentInstancesInterleaveAndStopCancelsWaitingPreparedTasks() {
        List<SweepTask<?>> tasks = new ArrayList<>(); List<PreparedSweep> ready = new ArrayList<>(); List<Integer> finishes = new ArrayList<>();
        SweepCoordinator c = new SweepCoordinator((id, r) -> {
            SweepFixture b = new SweepFixture(); b.add("a"); b.add("b");
            SweepTask<List<Integer>> task = new SweepTask<>(id, r, b, new DefaultSweepPolicy<List<Integer>>() {
                int batches;
                public List<Integer> createState(SweepContext context) { return new ArrayList<>(); }
                public void afterBatch(BatchContext context, List<Integer> state, BatchResult result) { state.add(++batches); }
                public void onFinish(SweepContext context, List<Integer> state, SweepOutcome outcome) { assertEquals(batches, state.size()); finishes.add(batches); }
            }); tasks.add(task); return task;
        }, ready::add, (id, error) -> { throw new AssertionError(error); });
        SweepRequest r = SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.DELETE).batchSize(1).build();
        c.submit(r); c.submit(r); c.submit(r); c.tick(); assertEquals(3, c.active());
        ready.get(0).start(); ready.get(1).start(); tasks.get(0).runNextBatch(); tasks.get(1).runNextBatch(); tasks.get(0).runNextBatch();
        assertEquals(2, c.active()); c.stop(); assertEquals(0, c.active());
        assertEquals(Arrays.asList(2, 1, 0), finishes); assertThrows(IllegalStateException.class, ready.get(2)::start);
    }

    @Test public void failedFactoryDoesNotLoseLaterRequestsOrInventFinishCallback() {
        int[] created = {0}; List<String> failures = new ArrayList<>();
        SweepCoordinator c = new SweepCoordinator((id, r) -> {
            if (++created[0] == 1) throw new IllegalStateException("constructor failed");
            return new SweepTask<>(id, r, new SweepFixture(), new DefaultSweepPolicy<>());
        }, p -> {}, (id, error) -> failures.add(error.getMessage()));
        SweepRequest r = SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.SCAN_ONLY).build();
        c.submit(r); c.submit(r); c.tick();
        assertEquals(Collections.singletonList("constructor failed"), failures); assertEquals(2, created[0]); assertEquals(0, c.active());
    }

    @Test public void completionFailureDoesNotPreventOtherTasksFromReleasing() {
        List<SweepTask<?>> tasks = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        SweepCoordinator coordinator = new SweepCoordinator((id, request) -> {
            SweepTask<?> task = new SweepTask<>(id, request, new SweepFixture(), new DefaultSweepPolicy<>());
            tasks.add(task);
            return task;
        }, ready -> {}, (id, error) -> failures.add(error.getMessage()), task -> {
            throw new IllegalStateException("completion notification failed");
        });
        SweepRequest request = SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.RECYCLE).build();
        coordinator.submit(request); coordinator.submit(request); coordinator.tick();
        coordinator.stop();
        assertEquals(0, coordinator.active());
        assertEquals(2, failures.size());
        for (SweepTask<?> task : tasks) assertEquals(SweepPhase.CANCELLED, task.phase());
    }

    @Test public void crossThreadSubmissionIsRejected() throws Exception {
        SweepCoordinator c = new SweepCoordinator((id, r) -> null, p -> {}, (id, error) -> { throw new AssertionError(error); });
        ExecutionException error = assertThrows(ExecutionException.class, () -> CompletableFuture.runAsync(() -> c.submit(SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.DELETE).build())).get(3, TimeUnit.SECONDS));
        assertTrue(error.getCause() instanceof IllegalStateException);
    }
}
