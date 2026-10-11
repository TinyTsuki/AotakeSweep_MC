package xin.vanilla.aotake.network;

import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class SweepScheduleSyncTest {
    private long effective(long deadline, long interval, boolean disabled, boolean ready) throws Exception {
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.aotake.network.SweepScheduleSync");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing effective schedule synchronization", e);
        }
        Method method = type.getMethod("effectiveNextSweep", long.class, long.class, boolean.class, boolean.class);
        return ((Number) method.invoke(null, deadline, interval, disabled, ready)).longValue();
    }

    @Test public void runningReadyScheduleKeepsAuthoritativeDeadline() throws Exception {
        assertEquals(90000, effective(90000, 60000, false, true));
    }

    @Test public void disabledScheduleMustSendInvalidDeadlineInsteadOfOldTime() throws Exception {
        assertEquals(0, effective(90000, 60000, true, true));
    }

    @Test public void unfinishedRuleLoadCannotAdvertiseScheduledTime() throws Exception {
        assertEquals(0, effective(90000, 60000, false, false));
    }

    @Test public void nonPositiveIntervalAndDeadlineAreHidden() throws Exception {
        assertEquals(0, effective(90000, 0, false, true));
        assertEquals(0, effective(90000, -1, false, true));
        assertEquals(0, effective(0, 60000, false, true));
        assertEquals(0, effective(-1, 60000, false, true));
    }
}
