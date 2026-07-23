package xin.vanilla.aotake.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RateLimitedErrorLoggerTest {
    @Test
    public void suppressesIdenticalFailuresWithinWindow() {
        RateLimitedErrorLogger logger = new RateLimitedErrorLogger(1000L);
        RuntimeException failure = new RuntimeException("same");

        RateLimitedErrorLogger.LogDecision first = logger.record("sweep", failure, 100L);
        RateLimitedErrorLogger.LogDecision repeated = logger.record("sweep", failure, 200L);
        RateLimitedErrorLogger.LogDecision afterWindow = logger.record("sweep", failure, 1100L);

        assertTrue(first.shouldLog());
        assertFalse(repeated.shouldLog());
        assertTrue(afterWindow.shouldLog());
        assertEquals(1L, afterWindow.suppressedCount());
    }

    @Test
    public void doesNotMergeDifferentErrors() {
        RateLimitedErrorLogger logger = new RateLimitedErrorLogger(1000L);
        assertTrue(logger.record("check", new RuntimeException("one"), 100L).shouldLog());
        assertTrue(logger.record("check", new RuntimeException("two"), 200L).shouldLog());
    }
}
