package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import static org.junit.Assert.*;

public class SweepEntryPointTest {
    @Test public void chunkDelayKeepsSamePreparedTaskAndNeverCollectsAgain() {
        SweepFixture b = new SweepFixture(); b.add("a");
        int[] collect = {0}, finish = {0}, cleanup = {0};
        SweepTask<Object> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.CHUNK, SweepOperation.RECYCLE).build(), b,
                new DefaultSweepPolicy<Object>() {
                    public Object createState(SweepContext c) { return new Object(); }
                    Object state;
                    public List<EntityAccess> collectEntities(SweepContext c,Object s) { state=s; collect[0]++; return super.collectEntities(c,s); }
                    @Override public StageControl beforeCleanup(SweepContext c,Object s,List<EntityAccess> selected) { assertSame(state,s); cleanup[0]++; return StageControl.CONTINUE; }
                    public void onFinish(SweepContext c,Object s,SweepOutcome o) { assertSame(state,s); finish[0]++; }
                });
        SweepExecution execution = new SweepExecution(task, task.prepare(), 10, 25, 2, false);
        execution.tick(34); assertEquals(SweepPhase.READY, task.phase()); assertEquals(0, b.executed);
        execution.tick(35); assertEquals(SweepPhase.SUCCESS, task.phase());
        execution.tick(100); assertEquals(1, collect[0]); assertEquals(1, cleanup[0]); assertEquals(1, finish[0]); assertEquals(1,b.executed);
    }

    @Test public void delayedEntitiesAreRevalidatedWithoutRepeatingEvaluation() {
        SweepFixture backend = new SweepFixture();
        SweepFixture.Node removed = SweepFixture.node(backend.add("removed"));
        SweepFixture.Node moved = SweepFixture.node(backend.add("moved"));
        backend.add("remaining");
        int[] evaluated = {0};
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.CHUNK, SweepOperation.DELETE)
                .scope(SweepScope.world("overworld")).build(), backend, new DefaultSweepPolicy<Void>() {
            @Override public FlowControl beforeEntity(SweepEntityContext context, Void state) { evaluated[0]++; return FlowControl.CONTINUE; }
        });
        SweepExecution execution = new SweepExecution(task, task.prepare(), 0, 25, 1, false);
        removed.valid = false;
        moved.dimension = "nether";
        execution.tick(25);
        assertEquals(SweepPhase.SUCCESS, task.phase());
        assertEquals(3, evaluated[0]);
        assertEquals(1, task.outcome().processed());
        assertEquals(2, task.outcome().skipped());
    }
    @Test public void noticeOnlyCompletesWithoutCleanupAndCancelledDelayNeverStarts() {
        for (boolean notice : Arrays.asList(true,false)) {
            SweepFixture b=new SweepFixture(); b.add("a");
            SweepTask<Void> t=new SweepTask<>(UUID.randomUUID(),SweepRequest.builder(SweepTrigger.CHUNK,SweepOperation.RECYCLE).build(),b,new DefaultSweepPolicy<Void>());
            SweepExecution execution=new SweepExecution(t,t.prepare(),0,25,1,notice);
            if(!notice) t.cancel("reload");
            execution.tick(25);
            assertEquals(notice ? SweepPhase.SUCCESS : SweepPhase.CANCELLED,t.phase());
            assertFalse(b.events.contains("preflight")); assertEquals(0,b.executed); assertEquals(1,b.released);
        }
    }
    @Test public void configuredBatchCapKeepsBalancedSizesAndIntervals() {
        SweepFixture b=new SweepFixture() {
            public List<List<EntityAccess>> batches(SweepRequest r,List<EntityAccess> es) {
                return xin.vanilla.banira.common.util.CollectionUtils.splitToCollections(es,2,3);
            }
        };
        for(int i=0;i<10;i++) b.add("item"+i);
        List<Integer> sizes=new ArrayList<>();
        SweepTask<Void> t=new SweepTask<>(UUID.randomUUID(),SweepRequest.builder(SweepTrigger.SCHEDULED,SweepOperation.RECYCLE).build(),b,
                new DefaultSweepPolicy<Void>() { public void afterBatch(BatchContext c,Void s,BatchResult r){ sizes.add(r.processed()); } });
        SweepExecution execution=new SweepExecution(t,t.prepare(),0,0,5,false);
        execution.tick(0); execution.tick(4); assertEquals(Collections.singletonList(3),sizes);
        execution.tick(5); execution.tick(10); assertEquals(Arrays.asList(3,3,4),sizes);
        assertEquals(SweepPhase.SUCCESS,t.phase());
    }
}
