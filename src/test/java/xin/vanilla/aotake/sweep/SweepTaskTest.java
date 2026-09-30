package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SweepTaskTest {
    @Test public void worldQueryCandidatesSurviveCollectionCallbacksButNotTaskEnd() {
        for (boolean addAfter : new boolean[]{false, true}) {
            SweepFixture b = new SweepFixture(); b.add("a"); b.add("b");
            AtomicReference<EntityAccess> retained = new AtomicReference<>();
            AtomicReference<xin.vanilla.aotake.api.rule.WorldAccess> world = new AtomicReference<>();
            SweepTask<Void> t = task(b, new DefaultSweepPolicy<Void>() {
                private List<EntityAccess> query(SweepContext c) {
                    world.set(c.server().world("overworld").get());
                    List<EntityAccess> result = world.get().entities(0, 0, 0, 10, 10, 10);
                    retained.set(result.get(0));
                    return result;
                }
                public List<EntityAccess> collectEntities(SweepContext c, Void s) {
                    return addAfter ? new ArrayList<>() : query(c);
                }
                public void afterCollect(SweepContext c, Void s, List<EntityAccess> es) {
                    if (addAfter) es.addAll(query(c));
                }
                public void onFinish(SweepContext c, Void s, SweepOutcome outcome) {
                    assertEquals("a", retained.get().resourceId());
                    assertThrows(IllegalStateException.class, () -> world.get().entities(0, 0, 0, 10, 10, 10));
                }
            });
            run(t);
            assertEquals(SweepPhase.SUCCESS, t.phase()); assertEquals(2, t.outcome().processed());
            assertThrows(IllegalStateException.class, () -> retained.get().resourceId());
        }
    }

    @Test public void retirementKeepsEntityViewsReadableUntilTerminalCallbacksComplete() {
        for (boolean duringBatch : new boolean[]{false, true}) {
            SweepFixture b = new SweepFixture() {
                public void execute(BatchContext c, Progress p) {
                    super.execute(c, p); if (duringBatch) usable = false;
                }
            };
            b.add("a"); b.add("b");
            AtomicReference<EntityAccess> retained = new AtomicReference<>();
            SweepTask<Void> t = task(b, new DefaultSweepPolicy<Void>() {
                public void afterCollect(SweepContext c, Void s, List<EntityAccess> es) { retained.set(es.get(0)); }
                public void afterBatch(BatchContext c, Void s, BatchResult r) {
                    assertEquals("a", c.entities().get(0).resourceId()); b.events.add("read-after");
                }
                public void onFinish(SweepContext c, Void s, SweepOutcome o) {
                    assertEquals("a", retained.get().resourceId()); b.events.add("read-finish");
                }
            });
            PreparedSweep ready = t.prepare();
            if (!duringBatch) b.usable = false;
            ready.start();
            while (t.phase() == SweepPhase.RUNNING) t.runNextBatch();
            assertEquals(SweepPhase.CANCELLED, t.phase()); assertEquals(duringBatch ? 1 : 0, b.executed);
            assertTrue(b.events.contains("read-finish")); assertEquals(0, b.faults);
            assertThrows(IllegalStateException.class, () -> retained.get().resourceId());
        }
    }

    private SweepRequest request() { return SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.RECYCLE).batchSize(1).build(); }
    private <S> SweepTask<S> task(SweepFixture b, SweepPolicy<S> p) { return new SweepTask<>(UUID.randomUUID(), request(), b, p); }
    private void run(SweepTask<?> task) {
        PreparedSweep prepared = task.prepare();
        if (prepared != null) { prepared.start(); while (task.phase() == SweepPhase.RUNNING) task.runNextBatch(); }
    }

    @Test public void fullOrderCopiesSourceAndReleasesStableViewsAtFinish() {
        SweepFixture b = new SweepFixture(); b.add("a"); b.add("b");
        AtomicReference<EntityAccess> retained = new AtomicReference<>();
        SweepTask<List<String>> task = task(b, new DefaultSweepPolicy<List<String>>() {
            public List<String> createState(SweepContext c) { b.events.add("state"); return b.events; }
            public StageControl beforeCollect(SweepContext c, List<String> s) { s.add("beforeCollect"); return StageControl.CONTINUE; }
            public List<EntityAccess> collectEntities(SweepContext c, List<String> s) { s.add("collect"); return super.collectEntities(c, s); }
            public void afterCollect(SweepContext c, List<String> s, List<EntityAccess> es) { s.add("afterCollect"); es.add(es.get(0)); }
            public StageControl beforeFilter(SweepContext c, List<String> s, List<EntityAccess> es) { s.add("beforeFilter"); retained.set(es.get(0)); return StageControl.CONTINUE; }
            public FlowControl beforeEntity(SweepEntityContext c, List<String> s) { s.add("before:" + c.entity().resourceId()); return FlowControl.CONTINUE; }
            public EntityDisposition evaluateEntity(SweepEntityContext c, List<String> s) { s.add("eval:" + c.entity().resourceId()); return super.evaluateEntity(c, s); }
            public EntityEvaluation afterEntity(SweepEntityContext c, List<String> s, EntityEvaluation e) { s.add("after:" + c.entity().resourceId()); return e; }
            public List<EntityAccess> finishFiltering(SweepContext c, List<String> s, List<EntityEvaluation> es) { s.add("finishFiltering"); return super.finishFiltering(c, s, es); }
            public void afterFilter(SweepContext c, List<String> s, List<EntityAccess> es) { s.add("afterFilter"); }
            public StageControl beforeCleanup(SweepContext c, List<String> s, List<EntityAccess> es) { s.add("beforeCleanup"); return StageControl.CONTINUE; }
            public FlowControl beforeBatch(BatchContext c, List<String> s) { s.add("beforeBatch" + c.index()); return FlowControl.CONTINUE; }
            public void afterBatch(BatchContext c, List<String> s, BatchResult r) { s.add("afterBatch" + c.index()); assertEquals(1, r.processed()); }
            public void onFinish(SweepContext c, List<String> s, SweepOutcome o) { s.add("finish"); assertEquals(7, c.generationId()); }
        });
        run(task);
        assertEquals(Arrays.asList("state", "beforeCollect", "collect", "afterCollect", "beforeFilter", "before:a", "eval:a", "after:a", "before:b", "eval:b", "after:b", "finishFiltering", "afterFilter", "preflight", "beforeCleanup", "beforeBatch0", "execute0", "afterBatch0", "beforeBatch1", "execute1", "afterBatch1", "finish"), b.events);
        assertEquals(2, b.source.size()); assertEquals(2, task.outcome().processed());
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(1, b.released); assertEquals(1, b.flushed);
        assertTrue(task.outcome().pendingPersistence());
        assertThrows(IllegalStateException.class, () -> retained.get().resourceId());
        assertThrows(IllegalStateException.class, task::runNextBatch);
    }

    @Test public void explicitDecisionsAndSkipDoNotInflateDefaultThresholds() {
        SweepFixture b = new SweepFixture();
        for (String id : Arrays.asList("default", "keep", "clean", "skip")) SweepFixture.node(b.add(id)).junk = false;
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public FlowControl beforeEntity(SweepEntityContext c, Void s) { return c.entity().resourceId().equals("skip") ? FlowControl.SKIP : FlowControl.CONTINUE; }
            public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) {
                return c.entity().resourceId().equals("keep") ? EntityDisposition.KEEP : c.entity().resourceId().equals("clean") ? EntityDisposition.CLEAN : EntityDisposition.DEFAULT;
            }
            public EntityEvaluation afterEntity(SweepEntityContext c, Void s, EntityEvaluation e) { if (e.skipped()) b.events.add("skipped-after"); return e; }
            public void afterFilter(SweepContext c, Void s, List<EntityAccess> es) { assertEquals("clean", es.get(0).resourceId()); assertEquals(1, es.size()); }
        });
        run(task);
        assertEquals(1, b.filtered); assertEquals(1, task.outcome().selected()); assertTrue(b.events.contains("skipped-after"));
    }

    @Test public void skippedCannotBeResurrectedAndUnassessedEntitiesCannotBeInjected() {
        SweepFixture b = new SweepFixture(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public FlowControl beforeEntity(SweepEntityContext c, Void s) { return FlowControl.SKIP; }
            public EntityEvaluation afterEntity(SweepEntityContext c, Void s, EntityEvaluation e) { return EntityEvaluation.of(c.entity(), EntityDisposition.CLEAN); }
        });
        run(task); assertEquals(SweepPhase.FAILED, task.phase()); assertEquals(0, b.executed);
        SweepFixture b2 = new SweepFixture(); b2.add("a");
        SweepTask<Void> other = task(b2, new DefaultSweepPolicy<Void>() {
            public void afterFilter(SweepContext c, Void s, List<EntityAccess> es) { es.add(SweepFixture.view(new SweepFixture.Node("injected"), () -> {})); }
        });
        run(other); assertEquals(SweepPhase.FAILED, other.phase()); assertEquals(0, b2.executed);
    }

    @Test public void failureDuringStateCreationStillFinishesWithNullStateExactlyOnce() {
        SweepFixture b = new SweepFixture();
        SweepTask<Object> task = task(b, new DefaultSweepPolicy<Object>() {
            public Object createState(SweepContext c) { throw new IllegalStateException("state failed"); }
            public void onFinish(SweepContext c, Object s, SweepOutcome o) { assertNull(s); b.events.add("finish"); throw new IllegalArgumentException("finish failed"); }
        });
        run(task); task.cancel("again");
        assertEquals(Collections.singletonList("finish"), b.events);
        assertEquals("state failed", task.outcome().failure()); assertEquals(1, task.outcome().diagnostics().size());
        assertEquals(1, b.released); assertEquals(1, b.flushed);
    }

    @Test public void evaluationExceptionDoesNotInvokeAfterEntity() {
        SweepFixture b = new SweepFixture(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) { throw new IllegalStateException("eval failed"); }
            public EntityEvaluation afterEntity(SweepEntityContext c, Void s, EntityEvaluation e) { fail("afterEntity on failure"); return e; }
        });
        run(task); assertEquals(SweepPhase.FAILED, task.phase()); assertEquals(0, b.executed);
    }

    @Test public void defaultScanUsesEntityEvaluationTimeAndFinalDispositionsForCounting() {
        SweepFixture b = new SweepFixture();
        EntityAccess first = b.add("first"), second = b.add("second");
        SweepFixture.node(first).junk = false; SweepFixture.node(second).junk = false;
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public EntityEvaluation afterEntity(SweepEntityContext c, Void s, EntityEvaluation e) {
                if (c.entity().resourceId().equals("first")) SweepFixture.node(first).junk = true;
                return c.entity().resourceId().equals("second") ? EntityEvaluation.of(c.entity(), EntityDisposition.KEEP) : e;
            }
        });
        run(task);
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(0, task.outcome().selected());
    }

    @Test public void skippedAndCancelledBatchesAlwaysReportWithoutExecuting() {
        for (FlowControl decision : Arrays.asList(FlowControl.SKIP, FlowControl.CANCEL)) {
            SweepFixture b = new SweepFixture(); b.add("a"); b.add("b");
            SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
                public FlowControl beforeBatch(BatchContext c, Void s) { return decision; }
                public void afterBatch(BatchContext c, Void s, BatchResult r) {
                    assertEquals(decision == FlowControl.SKIP ? 1 : 0, r.skipped());
                    assertEquals(decision == FlowControl.CANCEL, r.cancelled()); b.events.add("after");
                }
            });
            run(task); assertEquals(0, b.executed);
            assertEquals(decision == FlowControl.SKIP ? 2 : 1, Collections.frequency(b.events, "after"));
            assertEquals(decision == FlowControl.SKIP ? 0 : 2, task.outcome().remaining());
        }
    }

    @Test public void storedCallbackContextsExpireEvenDuringLaterCallbacks() {
        SweepFixture b = new SweepFixture(); b.add("a");
        AtomicReference<SweepContext> context = new AtomicReference<>();
        AtomicReference<SweepEntityContext> entity = new AtomicReference<>();
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public Void createState(SweepContext c) { context.set(c); return null; }
            public FlowControl beforeEntity(SweepEntityContext c, Void s) { entity.set(c); assertThrows(IllegalStateException.class, () -> context.get().server()); return FlowControl.CONTINUE; }
            public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) { assertThrows(IllegalStateException.class, () -> entity.get().entity()); return EntityDisposition.DEFAULT; }
        });
        run(task); assertEquals(SweepPhase.SUCCESS, task.phase());
        assertThrows(IllegalStateException.class, () -> context.get().taskId());
    }

    @Test public void reentrantCancellationDoesNotDropNewlyCreatedState() {
        SweepFixture b = new SweepFixture();
        SweepTask<?>[] handle = new SweepTask<?>[1];
        Object expected = new Object();
        SweepTask<Object> task = task(b, new DefaultSweepPolicy<Object>() {
            public Object createState(SweepContext c) { handle[0].cancel("cancel during state"); return expected; }
            public void onFinish(SweepContext c, Object s, SweepOutcome o) { assertSame(expected, s); }
        });
        handle[0] = task; run(task);
        assertEquals(SweepPhase.CANCELLED, task.phase()); assertEquals(1, b.released);
    }

    @Test public void nonFatalErrorAlsoReachesFinishAndEngineCleanup() {
        SweepFixture b = new SweepFixture(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) { throw new AssertionError("policy assertion"); }
            public void onFinish(SweepContext c, Void s, SweepOutcome o) { b.events.add("finish"); }
        });
        run(task);
        assertEquals(SweepPhase.FAILED, task.phase()); assertEquals("policy assertion", task.outcome().failure());
        assertEquals(Collections.singletonList("finish"), b.events); assertEquals(1, b.released); assertEquals(1, b.flushed);
    }

    @Test public void returnedEquivalentEntityViewKeepsTaskLifetimeGuard() {
        SweepFixture b = new SweepFixture(); EntityAccess original = b.add("a");
        AtomicReference<EntityAccess> result = new AtomicReference<>();
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public EntityEvaluation afterEntity(SweepEntityContext c, Void s, EntityEvaluation e) { return EntityEvaluation.of(original, EntityDisposition.CLEAN); }
            public void afterFilter(SweepContext c, Void s, List<EntityAccess> es) { result.set(es.get(0)); }
        });
        run(task); assertEquals(SweepPhase.SUCCESS, task.phase());
        assertThrows(IllegalStateException.class, () -> result.get().resourceId());
    }

    @Test public void preflightRevalidatesDelayedCandidatesWithoutRepeatingEvaluationHooks() {
        SweepFixture b = new SweepFixture(); EntityAccess e = b.add("a");
        int[] evaluations = {0};
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) { evaluations[0]++; return EntityDisposition.DEFAULT; }
        });
        PreparedSweep ready = task.prepare(); SweepFixture.node(e).valid = false; ready.start();
        while (task.phase() == SweepPhase.RUNNING) task.runNextBatch();
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(1, evaluations[0]);
        assertEquals(0, task.outcome().processed()); assertEquals(1, task.outcome().skipped());
    }

    @Test public void emptyOperationsHaveDifferentSideEffects() {
        for (SweepOperation op : SweepOperation.values()) {
            SweepFixture b = new SweepFixture();
            SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, op).build(), b, new DefaultSweepPolicy<>());
            run(task);
            assertEquals(op == SweepOperation.RECYCLE ? 1 : 0, b.executed);
            assertEquals(SweepPhase.SUCCESS, task.phase());
            assertEquals(op != SweepOperation.SCAN_ONLY, b.events.contains("preflight"));
        }
    }

    @Test public void beforeCleanupCancelPrecedesAllSideEffectsAndPreparedTokenIsSingleUse() {
        SweepFixture b = new SweepFixture(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public StageControl beforeCleanup(SweepContext c, Void s, List<EntityAccess> es) { return StageControl.CANCEL; }
        });
        PreparedSweep p = task.prepare(); p.start();
        assertEquals(SweepPhase.CANCELLED, task.phase()); assertEquals(0, b.executed);
        assertThrows(IllegalStateException.class, p::start);
    }

    @Test public void retirementBetweenPrepareAndStartCannotExecute() {
        SweepFixture b = new SweepFixture(); b.add("a"); SweepTask<Void> task = task(b, new DefaultSweepPolicy<>());
        PreparedSweep p = task.prepare(); b.usable = false; p.start();
        assertEquals(SweepPhase.CANCELLED, task.phase()); assertEquals(0, b.executed); assertEquals(1, b.released);
    }

    @Test public void partialBatchFailureRetainsCommittedCountsEvenWhenAfterCallbackFails() {
        SweepFixture b = new SweepFixture(); b.add("a"); b.add("b"); b.failExecution = true; b.failFlush = true;
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.RECYCLE).batchSize(2).build(), b, new DefaultSweepPolicy<Void>() {
            public void afterBatch(BatchContext c, Void s, BatchResult r) { assertEquals(1, r.processed()); assertEquals(1, r.committed()); assertEquals(1, r.remaining()); throw new IllegalStateException("after failed"); }
        });
        run(task);
        assertEquals("execute failed", task.outcome().failure()); assertEquals(1, task.outcome().processed());
        assertEquals(1, task.outcome().remaining()); assertEquals(1, task.outcome().pendingRemoval());
        assertEquals(2, task.outcome().diagnostics().size()); assertEquals(1, b.released);
    }

    @Test public void scopeMayOnlyChangeBeforeCollectAndCannotExceedAuthorization() {
        SweepFixture b = new SweepFixture(); b.add("a"); SweepFixture.node(b.add("b")).dimension = "nether";
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public StageControl beforeCollect(SweepContext c, Void s) { c.requestScope(SweepScope.world("overworld")); return StageControl.CONTINUE; }
            public void afterFilter(SweepContext c, Void s, List<EntityAccess> es) { assertEquals(1, es.size()); assertThrows(IllegalStateException.class, () -> c.requestScope(SweepScope.all())); }
        });
        run(task); assertEquals(1, task.outcome().scanned());
        SweepTask<Void> unauthorized = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.DELETE).scope(SweepScope.world("overworld")).build(), new SweepFixture(), new DefaultSweepPolicy<Void>() {
            public StageControl beforeCollect(SweepContext c, Void s) { c.requestScope(SweepScope.all()); return StageControl.CONTINUE; }
        });
        run(unauthorized); assertEquals(SweepPhase.FAILED, unauthorized.phase());
    }

    @Test public void normalizedMultipartAndInvalidCandidatesAreRemovedBeforeHooks() {
        SweepFixture b = new SweepFixture(); EntityAccess parent = b.add("parent");
        SweepFixture.node(b.add("part")).parent = SweepFixture.node(parent);
        SweepFixture.node(b.add("dead")).valid = false;
        runAndAssertSelected(b, false, false, 1);
    }

    @Test public void bypassAndPrefilteredStillRunHooksButSkipOnlyDefaultBusinessFilter() {
        for (boolean prefiltered : new boolean[]{false, true}) {
            SweepFixture b = new SweepFixture(); SweepFixture.node(b.add("protected")).safe = true;
            runAndAssertSelected(b, !prefiltered, prefiltered, 1); assertEquals(0, b.filtered);
        }
    }
    private void runAndAssertSelected(SweepFixture b, boolean ignore, boolean pre, int count) {
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.DELETE).ignoreFilter(ignore).preFiltered(pre).build(), b, new DefaultSweepPolicy<>());
        run(task); assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(count, task.outcome().selected());
    }
}
