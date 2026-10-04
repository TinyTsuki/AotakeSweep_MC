package xin.vanilla.aotake.util;

import org.junit.Test;
import java.lang.management.ManagementFactory;
import java.util.*;
import static org.junit.Assert.*;

public class RecyclingCandidatesTest {
    @Test public void canonicalIdentityIsEvaluatedOnceEvenWhenFirstOccurrenceIsInvalid() {
        Object parent = new Object(), child = new Object(), other = new Object();
        int[] checks = {0};
        RecyclingCandidates<Object> index = new RecyclingCandidates<>();
        List<Object> gathered = index.gather(Arrays.asList(child, parent, child, other),
                entity -> entity == child ? parent : entity, entity -> ++checks[0] > 1);
        assertEquals(Collections.singletonList(other), gathered);
        assertEquals(2, checks[0]);
        assertFalse(index.ready(parent));
        assertThrows(IllegalArgumentException.class, () -> index.decide(parent, true));
        assertFalse(index.ready(other));
        index.decide(other, false);
        assertTrue(index.ready(other));
        assertFalse(index.recycle(other));
        assertFalse(index.ready(parent));
    }

    @Test public void unpreparedAndClearedIdentitiesCannotBeCommitted() {
        Object entity = new Object(), unknown = new Object();
        RecyclingCandidates<Object> index = new RecyclingCandidates<>();
        index.gather(Collections.singletonList(entity), value -> value, value -> true);
        assertFalse(index.ready(entity));
        assertThrows(IllegalStateException.class, () -> index.recycle(entity));
        assertThrows(IllegalArgumentException.class, () -> index.decide(unknown, true));
        index.decide(entity, true);
        assertTrue(index.recycle(entity));
        index.clear();
        assertFalse(index.ready(entity));
        assertThrows(IllegalStateException.class, () -> index.recycle(entity));
        assertEquals(Collections.singletonList(entity), index.gather(Collections.singletonList(entity), value -> value, value -> true));
        assertFalse(index.ready(entity));
    }

    @Test public void equalValuesWithDifferentIdentitiesRemainDistinct() {
        String first = new String("same"), second = new String("same");
        RecyclingCandidates<String> index = new RecyclingCandidates<>();
        assertEquals(2, index.gather(Arrays.asList(first, second, first), value -> value, value -> true).size());
        index.decide(first, true);
        index.decide(second, false);
        assertTrue(index.recycle(first));
        assertFalse(index.recycle(second));
    }

    @Test public void freshBatchUsesOneIdentityTableAllocation() {
        com.sun.management.ThreadMXBean meter = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(meter.isThreadAllocatedMemorySupported());
        meter.setThreadAllocatedMemoryEnabled(true);
        List<Object> subjects = new ArrayList<>();
        for (int i = 0; i < 1024; i++) subjects.add(new Object());
        for (int i = 0; i < 8; i++) populate(subjects);
        long thread = Thread.currentThread().getId(), before = meter.getThreadAllocatedBytes(thread);
        populate(subjects);
        long allocated = meter.getThreadAllocatedBytes(thread) - before;
        assertTrue("Duplicate identity tables allocated " + allocated + " bytes", allocated < 60000);
    }

    private static void populate(List<Object> subjects) {
        RecyclingCandidates<Object> index = new RecyclingCandidates<>();
        for (Object entity : index.gather(subjects, value -> value, value -> true)) index.decide(entity, true);
        assertTrue(index.ready(subjects.get(0)));
        index.clear();
    }
}
