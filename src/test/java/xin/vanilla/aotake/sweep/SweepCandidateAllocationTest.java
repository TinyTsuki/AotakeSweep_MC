package xin.vanilla.aotake.sweep;

import org.junit.Test;
import xin.vanilla.aotake.api.rule.EntityAccess;
import java.lang.management.ManagementFactory;
import java.util.List;
import static org.junit.Assert.*;

public class SweepCandidateAllocationTest {
    @Test public void knownCollectionSizeAvoidsGrowingIdentityTablesAndArrays() {
        com.sun.management.ThreadMXBean meter = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(meter.isThreadAllocatedMemorySupported());
        meter.setThreadAllocatedMemoryEnabled(true);
        SweepFixture fixture = new SweepFixture();
        for (int i = 0; i < 1024; i++) fixture.add("entity" + i);
        for (int i = 0; i < 8; i++) { populate(fixture.source, false); populate(fixture.source, true); }
        long thread = Thread.currentThread().getId(), before = meter.getThreadAllocatedBytes(thread);
        populate(fixture.source, false);
        long growing = meter.getThreadAllocatedBytes(thread) - before;
        before = meter.getThreadAllocatedBytes(thread);
        populate(fixture.source, true);
        long reserved = meter.getThreadAllocatedBytes(thread) - before;
        assertTrue("Reservation did not remove resizing allocations: " + growing + " / " + reserved, reserved + 16384 < growing);
    }

    private static void populate(List<EntityAccess> source, boolean reserve) {
        SweepCandidates candidates = new SweepCandidates(entity -> entity);
        if (reserve) candidates.reserve(source.size());
        for (EntityAccess entity : source) candidates.add(entity);
        assertEquals(source.size(), candidates.records().size());
        for (int i = 0; i < source.size(); i++) assertSame(source.get(i), candidates.records().get(i).entity);
        candidates.clear();
    }
}
