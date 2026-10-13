package xin.vanilla.aotake.client.minimap;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

public class SweepCountdownStateTest {
    private Object state(AtomicLong clock) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.aotake.client.minimap.SweepCountdownState");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing authoritative countdown state", e);
        }
        return type.getConstructor(LongSupplier.class).newInstance((LongSupplier) clock::get);
    }

    private Object call(Object state, String name, Object... args) throws Exception {
        for (Method method : state.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(state, args);
        }
        throw new AssertionError("Missing countdown method " + name);
    }

    private long begin(Object state) throws Exception {
        return ((Number) call(state, "beginConnection")).longValue();
    }

    private boolean accept(Object state, long generation, long now, long deadline, long interval) throws Exception {
        return (Boolean) call(state, "accept", generation, now, deadline, interval);
    }

    private String time(Object state) throws Exception {
        return (String) call(state, "formattedTime");
    }

    @Test
    public void hiddenUntilCurrentConnectionHasReceivedSync() throws Exception {
        Object state = state(new AtomicLong());
        assertEquals("", time(state));
        assertEquals(-1L, call(state, "remainingSeconds"));
        begin(state);
        assertEquals("", time(state));
    }

    @Test
    public void positiveRemainderRoundsUpAndNeverExtrapolates() throws Exception {
        AtomicLong clock = new AtomicLong();
        Object state = state(clock);
        assertTrue(accept(state, begin(state), 1000, 2001, 60000));
        assertEquals("00:02", time(state));
        clock.set(1_000_000L);
        assertEquals("00:01", time(state));
        clock.set(1_001_000_000L);
        assertEquals("00:00", time(state));
        clock.set(61_001_000_000L);
        assertEquals("00:00", time(state));
    }

    @Test
    public void subMillisecondElapsedTimeCannotPrematurelyReachZero() throws Exception {
        AtomicLong clock = new AtomicLong();
        Object state = state(clock);
        assertTrue(accept(state, begin(state), 1000, 1001, 60000));
        clock.set(999_999L);
        assertEquals("00:01", time(state));
        clock.set(1_000_000L);
        assertEquals("00:00", time(state));
    }

    @Test
    public void disabledScheduleClearsLastValidTime() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 61000, 60000);
        assertEquals("01:00", time(state));
        assertTrue(accept(state, generation, 2000, 0, 60000));
        assertEquals("", time(state));
        assertTrue(accept(state, generation, 3000, 63000, 60000));
        assertEquals("01:00", time(state));
    }

    @Test
    public void nonPositiveIntervalCannotDisplayScheduledTime() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 61000, 0);
        assertEquals("", time(state));
        accept(state, generation, 1000, 61000, -1);
        assertEquals("", time(state));
    }

    @Test
    public void authoritativeDelayCanExtendTime() throws Exception {
        AtomicLong clock = new AtomicLong();
        Object state = state(clock);
        long generation = begin(state);
        accept(state, generation, 1000, 11000, 60000);
        clock.set(5_000_000_000L);
        assertEquals("00:05", time(state));
        accept(state, generation, 6000, 66000, 60000);
        assertEquals("01:00", time(state));
    }

    @Test
    public void hourThresholdAndSevenDaysUseHours() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 3_600_000, 7L * 86400000);
        assertEquals("59:59", time(state));
        accept(state, generation, 1000, 3_601_000, 7L * 86400000);
        assertEquals("01:00:00", time(state));
        accept(state, generation, 1000, 1000 + 7L * 86400000, 7L * 86400000);
        assertEquals("168:00:00", time(state));
    }

    @Test
    public void oldGenerationCannotReplaceNewServerTime() throws Exception {
        Object state = state(new AtomicLong());
        long old = begin(state);
        accept(state, old, 1000, 11000, 60000);
        long current = begin(state);
        assertEquals("", time(state));
        accept(state, current, 1000, 61000, 60000);
        assertFalse(accept(state, old, 1000, 11000, 60000));
        assertEquals("01:00", time(state));
    }

    @Test
    public void resetInvalidatesQueuedSyncFromExitedConnection() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 61000, 60000);
        call(state, "reset");
        assertEquals("", time(state));
        assertFalse(accept(state, generation, 1000, 61000, 60000));
        assertEquals("", time(state));
    }

    @Test
    public void serverClockOffsetsDoNotChangeRelativeCountdown() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 61000, 60000);
        assertEquals("01:00", time(state));
        accept(state, generation, 1_000_000, 1_060_000, 60000);
        assertEquals("01:00", time(state));
    }

    @Test
    public void normalNanoClockWrapAndNegativeOriginStillExpire() throws Exception {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 1_000_000L);
        Object state = state(clock);
        long generation = begin(state);
        accept(state, generation, 1000, 1003, 60000);
        clock.addAndGet(3_000_000L);
        assertEquals("00:00", time(state));
        clock.set(-500_000L);
        accept(state, generation, 1000, 1001, 60000);
        clock.set(500_000L);
        assertEquals("00:00", time(state));
    }

    @Test
    public void malformedTimestampDifferenceHidesInsteadOfWrapping() throws Exception {
        Object state = state(new AtomicLong());
        long generation = begin(state);
        accept(state, generation, 1000, 61000, 60000);
        assertFalse(accept(state, generation, Long.MIN_VALUE, Long.MAX_VALUE, 60000));
        assertEquals("", time(state));
    }

    @Test
    public void nanosecondConversionOverflowHidesInsteadOfWrapping() throws Exception {
        Object state = state(new AtomicLong());
        assertFalse(accept(state, begin(state), 1, Long.MAX_VALUE, 60000));
        assertEquals("", time(state));
    }

    @Test
    public void alreadyExpiredPositiveDeadlineWaitsForNextServerUpdate() throws Exception {
        Object state = state(new AtomicLong());
        assertTrue(accept(state, begin(state), 5000, 4000, 60000));
        assertEquals("00:00", time(state));
    }
}
