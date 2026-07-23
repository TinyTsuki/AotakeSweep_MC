package xin.vanilla.aotake.util;

import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保留首次完整堆栈，并抑制短时间内完全相同的清理错误。
 */
public final class RateLimitedErrorLogger {
    private final long intervalMillis;
    private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

    public RateLimitedErrorLogger(long intervalMillis) {
        if (intervalMillis < 0) throw new IllegalArgumentException("intervalMillis must be non-negative");
        this.intervalMillis = intervalMillis;
    }

    public void log(Logger logger, String operation, Throwable error) {
        LogDecision decision = record(operation, error, System.currentTimeMillis());
        if (!decision.shouldLog()) return;

        String message = error.getMessage() == null ? "<no message>" : error.getMessage();
        if (decision.suppressedCount() > 0) {
            logger.error("{} failed [{}: {}]; suppressed {} identical failures",
                    operation, error.getClass().getName(), message, decision.suppressedCount(), error);
        } else {
            logger.error("{} failed [{}: {}]", operation, error.getClass().getName(), message, error);
        }
    }

    LogDecision record(String operation, Throwable error, long now) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        String key = operation + '\n' + error.getClass().getName() + '\n' + message;
        FailureState state = failures.computeIfAbsent(key, ignored -> new FailureState());
        synchronized (state) {
            if (!state.logged || now - state.lastLoggedAt >= intervalMillis) {
                long suppressed = state.suppressed;
                state.logged = true;
                state.lastLoggedAt = now;
                state.suppressed = 0;
                return new LogDecision(true, suppressed);
            }
            state.suppressed++;
            return new LogDecision(false, state.suppressed);
        }
    }

    static final class LogDecision {
        private final boolean shouldLog;
        private final long suppressedCount;

        private LogDecision(boolean shouldLog, long suppressedCount) {
            this.shouldLog = shouldLog;
            this.suppressedCount = suppressedCount;
        }

        boolean shouldLog() {
            return shouldLog;
        }

        long suppressedCount() {
            return suppressedCount;
        }
    }

    private static final class FailureState {
        private boolean logged;
        private long lastLoggedAt;
        private long suppressed;
    }
}
