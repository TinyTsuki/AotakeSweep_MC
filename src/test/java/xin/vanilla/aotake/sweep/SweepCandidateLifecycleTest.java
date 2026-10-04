package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import static org.junit.Assert.*;

public class SweepCandidateLifecycleTest {
    @Test public void identityIndexDeduplicatesAndEvaluationSnapshotsStayStable() {
        SweepFixture b = new SweepFixture(); EntityAccess e = b.add("a");
        SweepCandidates candidates = new SweepCandidates(b::identity);
        candidates.add(e); candidates.add(SweepFixture.view(SweepFixture.node(e), () -> {}));
        assertEquals(1, candidates.records().size());
        SweepCandidates.Record r = candidates.records().get(0);
        EntityEvaluation before = r.evaluation();
        r.update(true, EntityDisposition.KEEP);
        assertFalse(before.skipped()); assertTrue(r.evaluation().skipped());
        try { candidates.restrict(Collections.singletonList(e)); fail("Cannot select skipped entity"); }
        catch (IllegalArgumentException expected) {}
    }
    @Test public void selectionReusesCanonicalIdentityAndPreservesRequestedOrder() {
        SweepFixture b = new SweepFixture(); EntityAccess a = b.add("a"), z = b.add("z");
        SweepCandidates candidates = new SweepCandidates(b::identity); candidates.add(a); candidates.add(z);
        List<EntityAccess> selected = candidates.restrict(Arrays.asList(z,a,z));
        assertEquals(2, selected.size()); assertSame(z,selected.get(0)); assertSame(a,selected.get(1));
        assertSame(a,candidates.restrict(Collections.singletonList(a)).get(0));
        try { candidates.restrict(Collections.singletonList(b.add("unknown"))); fail("Cannot inject unknown entity"); }
        catch (IllegalArgumentException expected) {}
    }
    private static final class CanonicalBackend extends SweepFixture {
        int admissions;
        public boolean collectedCandidatesAreCanonical() { return true; }
        public EntityAccess normalize(EntityAccess e, SweepScope s, Runnable g) {
            admissions++; return super.normalize(e, s, g);
        }
    }
    @Test public void ownedDefaultCollectionAvoidsRedundantAdmission() {
        CanonicalBackend b = new CanonicalBackend(); b.add("a"); b.add("b");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<>());
        task.prepare();
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(2, task.outcome().selected());
        assertEquals("Default collection is already canonical", 0, b.admissions);
    }
    @Test public void afterCollectNativeMutationRequiresReadmissionEvenWithSameList() {
        CanonicalBackend b = new CanonicalBackend(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public void afterCollect(SweepContext c, Void s, List<EntityAccess> es) {
                SweepFixture.node(es.get(0)).valid = false;
            }
        });
        task.prepare();
        assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals(0, task.outcome().selected());
        assertEquals(1, b.admissions);
    }
    @Test public void customCollectionCallingSuperStillRequiresAdmission() {
        CanonicalBackend b = new CanonicalBackend(); b.add("a");
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public List<EntityAccess> collectEntities(SweepContext c, Void s) { return super.collectEntities(c, s); }
        });
        task.prepare(); assertEquals(1, b.admissions);
    }
    @Test public void callerProvidedCandidatesAlwaysRequireAdmission() {
        CanonicalBackend b = new CanonicalBackend(); EntityAccess e = b.add("a");
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(),
                SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.SCAN_ONLY).candidates(Arrays.asList(e,e)).build(),
                b, new DefaultSweepPolicy<>());
        task.prepare(); assertEquals(2, b.admissions); assertEquals(1, task.outcome().selected());
    }
    @Test public void customDefaultFilterSubsetReordersWithoutReevaluatingRules() {
        int[] evaluations = {0};
        SweepFixture b = new SweepFixture() {
            public DefaultSweepFilter.Entry<EntityAccess,Object> evaluate(SweepRequest r, EntityAccess e) {
                evaluations[0]++; return super.evaluate(r,e);
            }
        };
        b.add("a"); b.add("b"); b.add("c");
        List<String> selected = new ArrayList<>();
        SweepTask<Void> task = task(b, new DefaultSweepPolicy<Void>() {
            public FlowControl beforeEntity(SweepEntityContext c, Void s) { return c.index()==1?FlowControl.SKIP:FlowControl.CONTINUE; }
            public List<EntityAccess> finishFiltering(SweepContext c, Void s, List<EntityEvaluation> es) {
                assertEquals(2, super.finishFiltering(c,s,es).size());
                return super.finishFiltering(c,s,Arrays.asList(es.get(2),es.get(0),es.get(2)));
            }
            public void afterFilter(SweepContext c, Void s, List<EntityAccess> es) {
                for(EntityAccess e:es) selected.add(e.resourceId());
            }
        });
        task.prepare(); assertEquals(SweepPhase.SUCCESS,task.phase());
        assertEquals(Arrays.asList("c","a","c"),selected);
        assertEquals(2,task.outcome().selected()); assertEquals(2,evaluations[0]);
    }
    private SweepTask<Void> task(SweepFixture b, DefaultSweepPolicy<Void> policy) {
        return new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.SCAN_ONLY).build(), b, policy);
    }
}
