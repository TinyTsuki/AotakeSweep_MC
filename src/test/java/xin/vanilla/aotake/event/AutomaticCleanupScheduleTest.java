package xin.vanilla.aotake.event;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class AutomaticCleanupScheduleTest {
    private long savedSweep, savedChunk;
    private Object savedWarning;

    @Before public void saveState() throws Exception {
        savedSweep = EventHandlerProxy.getNextSweepTime();
        savedChunk = field("lastChunkCheckTime").getLong(null);
        savedWarning = field("lastCountdownWarningDispatchKey").get(null);
        EventHandlerProxy.resetCleanupState();
    }

    @After public void restoreState() throws Exception {
        EventHandlerProxy.setNextSweepTime(savedSweep);
        field("lastChunkCheckTime").setLong(null, savedChunk);
        field("lastCountdownWarningDispatchKey").set(null, savedWarning);
    }

    @Test public void pendingInitialLoadDoesNotConsumeScheduledOpportunity() {
        EventHandlerProxy.setNextSweepTime(50);
        assertFalse(EventHandlerProxy.beginScheduledSweep(100, 600, false));
        assertEquals(50, EventHandlerProxy.getNextSweepTime());
        assertTrue(EventHandlerProxy.beginScheduledSweep(101, 600, true));
        assertEquals(701, EventHandlerProxy.getNextSweepTime());
        assertFalse(EventHandlerProxy.beginScheduledSweep(102, 600, true));
        assertTrue(EventHandlerProxy.beginScheduledSweep(701, 600, true));
    }

    @Test public void disabledSweepAndFutureManualDeadlineRemainUnconsumed() {
        assertFalse(EventHandlerProxy.beginScheduledSweep(100, 0, true));
        assertFalse(EventHandlerProxy.beginScheduledSweep(100, -1, true));
        assertEquals(0, EventHandlerProxy.getNextSweepTime());
        EventHandlerProxy.setNextSweepTime(500);
        assertFalse(EventHandlerProxy.beginScheduledSweep(100, 600, true));
        assertEquals(500, EventHandlerProxy.getNextSweepTime());
    }

    @Test public void pendingInitialLoadAndPendingChunkTaskDoNotConsumeChunkOpportunity() throws Exception {
        assertFalse(EventHandlerProxy.beginChunkCheck(100, 50, false, false));
        assertEquals(0, field("lastChunkCheckTime").getLong(null));
        assertFalse(EventHandlerProxy.beginChunkCheck(100, 50, true, true));
        assertEquals(0, field("lastChunkCheckTime").getLong(null));
        assertTrue(EventHandlerProxy.beginChunkCheck(100, 50, true, false));
        assertEquals(100, field("lastChunkCheckTime").getLong(null));
        assertFalse(EventHandlerProxy.beginChunkCheck(149, 50, true, false));
        assertTrue(EventHandlerProxy.beginChunkCheck(150, 50, true, false));
    }

    @Test public void disabledChunkChecksRemainUnconsumed() throws Exception {
        assertFalse(EventHandlerProxy.beginChunkCheck(100, 0, true, false));
        assertFalse(EventHandlerProxy.beginChunkCheck(100, -1, true, false));
        assertEquals(0, field("lastChunkCheckTime").getLong(null));
    }

    @Test public void newServerResetsExpiredOpportunityAndCountdownDeduplication() throws Exception {
        long original = EventHandlerProxy.getNextSweepTime();
        Field chunk = field("lastChunkCheckTime");
        Field warning = field("lastCountdownWarningDispatchKey");
        long originalChunk = chunk.getLong(null);
        Object originalWarning = warning.get(null);
        try {
            EventHandlerProxy.setNextSweepTime(Long.MAX_VALUE);
            chunk.setLong(null, Long.MAX_VALUE);
            warning.set(null, "old-server-countdown");
            EventHandlerProxy.resetCleanupState();
            assertTrue("New server must not inherit the previous server deadline",
                    EventHandlerProxy.getNextSweepTime() <= System.currentTimeMillis());
            assertEquals(0, chunk.getLong(null));
            assertNull(warning.get(null));
        } finally {
            EventHandlerProxy.setNextSweepTime(original);
            chunk.setLong(null, originalChunk);
            warning.set(null, originalWarning);
        }
    }

    private static Field field(String name) throws Exception {
        Field value = EventHandlerProxy.class.getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }
}
