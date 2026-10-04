package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import static org.junit.Assert.*;

public class SweepTaskInvalidationTest {
    private SweepFixture backend() {
        return new SweepFixture() {
            public DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest request, EntityAccess entity) {
                return node(entity).valid ? super.evaluate(request, entity) : null;
            }
        };
    }
    private SweepTask<Void> task(SweepFixture backend, SweepPolicy<Void> policy) {
        return new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.RECYCLE).build(), backend, policy);
    }
    private void complete(SweepTask<?> task) {
        PreparedSweep ready = task.prepare();
        assertNotNull(task.outcome() == null ? null : task.outcome().failure(), ready);
        ready.start();
        while (task.phase() == SweepPhase.RUNNING) task.runNextBatch();
        assertEquals(SweepPhase.SUCCESS, task.phase());
    }

    @Test public void builtinEvaluationSkipsCandidatesRemovedByBeforeFilter() {
        SweepFixture backend = backend();
        backend.add("removed"); backend.add("live");
        complete(task(backend, new DefaultSweepPolicy<Void>() {
            public StageControl beforeFilter(SweepContext context, Void state, List<EntityAccess> entities) {
                SweepFixture.node(entities.get(0)).valid = false;
                return StageControl.CONTINUE;
            }
        }));
        assertEquals(1, backend.filtered);
        assertEquals(0, backend.faults);
        complete(task(backend, new DefaultSweepPolicy<>()));
        assertEquals(0, backend.faults);
    }

    @Test public void customEvaluationReportsInvalidCandidateAsSkippedBeforeAfterEntity() {
        SweepFixture backend = backend(); backend.add("removed"); backend.add("live");
        List<Boolean> skipped = new ArrayList<>();
        SweepTask<Void> task = task(backend, new DefaultSweepPolicy<Void>() {
            public FlowControl beforeEntity(SweepEntityContext context, Void state) {
                if (context.index() == 0) SweepFixture.node(context.entity()).valid = false;
                return FlowControl.CONTINUE;
            }
            public EntityEvaluation afterEntity(SweepEntityContext context, Void state, EntityEvaluation evaluation) {
                skipped.add(evaluation.skipped());
                return evaluation;
            }
        });
        complete(task);
        assertEquals(Arrays.asList(true, false), skipped);
        assertEquals(1, task.outcome().processed());
        assertEquals(0, backend.faults);
    }

    @Test public void earlierEvaluationMayInvalidateLaterCandidatesWithoutFailingTheGeneration() {
        SweepFixture backend = new SweepFixture() {
            public DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest request, EntityAccess entity) {
                if (node(entity).id.equals("first")) node(source.get(1)).valid = false;
                return node(entity).valid ? super.evaluate(request, entity) : null;
            }
        };
        backend.add("first"); backend.add("removed");
        SweepTask<Void> task = task(backend, new DefaultSweepPolicy<>());
        complete(task);
        assertEquals(1, task.outcome().processed());
        assertEquals(0, backend.faults);
    }

    @Test public void nullEvaluationStillChecksImmediateRetirement() {
        SweepFixture backend = new SweepFixture() {
            public DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest request, EntityAccess entity) {
                usable = false;
                return null;
            }
        };
        backend.add("a");
        SweepTask<Void> task = task(backend, new DefaultSweepPolicy<>());
        assertNull(task.prepare());
        assertEquals(SweepPhase.CANCELLED, task.phase());
        assertEquals(0, backend.faults);
        assertEquals(0, backend.executed);
    }
}
