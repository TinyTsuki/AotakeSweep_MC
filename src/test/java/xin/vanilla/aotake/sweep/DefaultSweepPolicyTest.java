package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import static org.junit.Assert.*;

public class DefaultSweepPolicyTest {
    @Test public void builtinDoesNotAllocateUnusedPerEntityHookFrames() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) bean;
        org.junit.Assume.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 8; i++) {
            prepareAllocated(allocation, new DefaultSweepPolicy<>());
            prepareAllocated(allocation, new DefaultSweepPolicy<Void>() {});
        }
        long builtin = prepareAllocated(allocation, new DefaultSweepPolicy<>());
        long inherited = prepareAllocated(allocation, new DefaultSweepPolicy<Void>() {});
        assertTrue("Unused hook frames: builtin=" + builtin + ", inherited=" + inherited,
                builtin + 1024L * 64 < inherited);
    }

    private long prepareAllocated(com.sun.management.ThreadMXBean allocation, DefaultSweepPolicy<Void> policy) {
        SweepFixture b = new SweepFixture();
        for (int i = 0; i < 1024; i++) b.add("e" + i);
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(),
                SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.SCAN_ONLY).build(), b, policy);
        long thread = Thread.currentThread().getId(), before = allocation.getThreadAllocatedBytes(thread);
        task.prepare();
        long bytes = allocation.getThreadAllocatedBytes(thread) - before;
        assertEquals(SweepPhase.SUCCESS, task.phase());
        assertEquals(1024, task.outcome().selected());
        return bytes;
    }

    @Test public void builtinStillCancelsImmediatelyWhenAnEntityRuleRetiresTheGeneration() {
        SweepFixture b = new SweepFixture() {
            int inspected;
            public DefaultSweepFilter.Entry<EntityAccess, Object> evaluate(SweepRequest r, EntityAccess e) {
                assertEquals("Must stop after the retiring rule", 1, ++inspected);
                usable = false;
                return super.evaluate(r, e);
            }
        };
        b.add("a"); b.add("b");
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(),
                SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.RECYCLE).build(), b, new DefaultSweepPolicy<>());
        assertNull(task.prepare());
        assertEquals(SweepPhase.CANCELLED, task.phase());
        assertEquals(0, b.executed); assertEquals(0, b.filtered); assertEquals(1, b.released);
    }

    @Test public void defaultAndInheritedPoliciesMatchBaselineTwoPassSelection() {
        Random random = new Random(41021);
        for (int scenario = 0; scenario < 100; scenario++) {
            SweepFixture backend = new SweepFixture();
            backend.typeLimit = random.nextInt(8); backend.safeLimit = random.nextInt(8);
            for (int i = 0, count = random.nextInt(40); i < count; i++) {
                SweepFixture.Node n = SweepFixture.node(backend.add("e" + i));
                n.safe = random.nextBoolean(); n.junk = random.nextBoolean();
                n.dimension = "world" + random.nextInt(3); n.type = "type" + random.nextInt(5);
            }
            List<String> expected = baseline(backend);
            List<String> actual = new ArrayList<>();
            SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.SCHEDULED, SweepOperation.SCAN_ONLY).build(), backend, new DefaultSweepPolicy<Void>() {
                public EntityDisposition evaluateEntity(SweepEntityContext c, Void s) { return super.evaluateEntity(c, s); }
                public List<EntityAccess> finishFiltering(SweepContext c, Void s, List<EntityEvaluation> es) { return super.finishFiltering(c, s, es); }
                public void afterFilter(SweepContext c, Void s, List<EntityAccess> selected) { for (EntityAccess e : selected) actual.add(e.resourceId()); }
            });
            assertNull(task.prepare());
            assertEquals(SweepPhase.SUCCESS, task.phase());
            assertEquals("scenario " + scenario, expected, actual);
            assertEquals(0, backend.executed);
        }
    }

    private List<String> baseline(SweepFixture backend) {
        Map<String, Integer> nonJunkTypes = new HashMap<>(), safeWorlds = new HashMap<>();
        for (EntityAccess e : backend.source) {
            SweepFixture.Node n = SweepFixture.node(e);
            if (!n.junk) nonJunkTypes.merge(n.type, 1, Integer::sum);
            if (n.safe) safeWorlds.merge(n.dimension, 1, Integer::sum);
        }
        Set<String> exceededTypes = new HashSet<>(), exceededWorlds = new HashSet<>();
        nonJunkTypes.forEach((type, count) -> { if (count > backend.typeLimit) exceededTypes.add(type); });
        safeWorlds.forEach((world, count) -> { if (count > backend.safeLimit) exceededWorlds.add(world); });
        List<String> result = new ArrayList<>();
        for (EntityAccess e : backend.source) {
            SweepFixture.Node n = SweepFixture.node(e);
            if ((!n.safe && n.junk) || (!n.junk && exceededTypes.contains(n.type)) || (n.safe && exceededWorlds.contains(n.dimension))) result.add(n.id);
        }
        return result;
    }

    @Test public void suppliedCandidatesAreSnapshottedAndEmptyListDoesNotCollectWorld() {
        SweepFixture b = new SweepFixture(); b.add("world");
        List<EntityAccess> supplied = new ArrayList<>();
        SweepRequest r = SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.SCAN_ONLY).candidates(supplied).build();
        supplied.add(b.source.get(0));
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), r, b, new DefaultSweepPolicy<>());
        task.prepare(); assertEquals(0, task.outcome().scanned()); assertEquals(0, task.outcome().selected());
    }

    @Test public void configurationIsImmutableAndResolvedBeforeStateCreation() {
        SweepFixture b = new SweepFixture(); b.typeLimit = 4;
        SweepTask<Void> task = new SweepTask<>(UUID.randomUUID(), SweepRequest.builder(SweepTrigger.COMMAND, SweepOperation.SCAN_ONLY).build(), b, new DefaultSweepPolicy<Void>() {
            public Void createState(SweepContext c) {
                assertEquals(4, c.configuration().get("limit"));
                assertThrows(UnsupportedOperationException.class, () -> c.configuration().put("limit", 0));
                b.typeLimit = 8; return null;
            }
            public void afterCollect(SweepContext c, Void s, List<EntityAccess> es) { assertEquals(4, c.configuration().get("limit")); }
        });
        task.prepare(); assertEquals(SweepPhase.SUCCESS, task.phase());
    }
}
