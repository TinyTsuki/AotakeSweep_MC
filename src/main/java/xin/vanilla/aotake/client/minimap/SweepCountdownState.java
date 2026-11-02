package xin.vanilla.aotake.client.minimap;

import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

public final class SweepCountdownState {
    private final LongSupplier nanoClock;
    private long generation;
    private long receivedAt;
    private long durationNanos;
    private long intervalMillis;
    private long formattedSeconds = Long.MIN_VALUE;
    private String formattedValue = "";
    private boolean ready;

    public SweepCountdownState(LongSupplier nanoClock) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public synchronized long beginConnection() {
        reset();
        return generation;
    }

    public synchronized boolean accept(long generation, long serverNowMillis, long nextSweepMillis, long intervalMillis) {
        if (generation != this.generation) return false;
        ready = false;
        if (nextSweepMillis <= 0 || intervalMillis <= 0) return true;
        try {
            long remainingMillis = Math.max(0, Math.subtractExact(nextSweepMillis, serverNowMillis));
            durationNanos = Math.multiplyExact(remainingMillis, 1_000_000L);
        } catch (ArithmeticException invalidTime) {
            return false;
        }
        receivedAt = nanoClock.getAsLong();
        this.intervalMillis = intervalMillis;
        ready = true;
        return true;
    }

    public synchronized long remainingSeconds() {
        if (!ready) return -1;
        long remaining = remainingNanos();
        return remaining / 1_000_000_000L + (remaining % 1_000_000_000L == 0 ? 0 : 1);
    }

    private long remainingNanos() {
        long elapsed = Math.max(0, nanoClock.getAsLong() - receivedAt);
        return Math.max(0, durationNanos - elapsed);
    }

    public synchronized double progress() {
        return snapshot().progress();
    }

    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    @lombok.AllArgsConstructor
    public static class Snapshot {
        String formattedTime;
        double progress;

    }

    public synchronized Snapshot snapshot() {
        if (!ready) return new Snapshot("", 0);
        long remaining = remainingNanos();
        long seconds = remaining / 1_000_000_000L + (remaining % 1_000_000_000L == 0 ? 0 : 1);
        return new Snapshot(formatCached(seconds), Math.max(0, Math.min(1, 1 - remaining / (intervalMillis * 1_000_000.0))));
    }

    public synchronized String formattedTime() {
        long seconds = remainingSeconds();
        if (seconds < 0) return "";
        return formatCached(seconds);
    }

    private String formatCached(long seconds) {
        if (seconds != formattedSeconds) {
            formattedValue = format(seconds);
            formattedSeconds = seconds;
        }
        return formattedValue;
    }

    private static String format(long seconds) {
        if (seconds >= 3600) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public synchronized void reset() {
        generation++;
        ready = false;
        receivedAt = 0;
        durationNanos = 0;
        intervalMillis = 0;
        formattedSeconds = Long.MIN_VALUE;
        formattedValue = "";
    }
}
