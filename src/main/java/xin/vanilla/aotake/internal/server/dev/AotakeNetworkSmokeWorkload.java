package xin.vanilla.aotake.internal.server.dev;

/** Controls the bounded sustained gameplay segment of the dev-only smoke. */
final class AotakeNetworkSmokeWorkload {
    static final int DURATION_TICKS = 320;
    static final int CONTAINER_BURST_STACKS = 27;
    static final int GLOBAL_ITEM_STACKS = 48;

    private final int startedAt;

    AotakeNetworkSmokeWorkload(int startedAt) {
        this.startedAt = startedAt;
    }

    boolean completeAt(int currentTick) {
        return currentTick - startedAt >= DURATION_TICKS;
    }

    boolean shouldTriggerSweepAt(int currentTick) {
        return elapsedTicksAt(currentTick) > 0 && elapsedTicksAt(currentTick) % 40 == 0;
    }

    boolean shouldCaptureAt(int currentTick) {
        return elapsedTicksAt(currentTick) > 0 && elapsedTicksAt(currentTick) % 80 == 0;
    }

    private int elapsedTicksAt(int currentTick) {
        return Math.max(0, currentTick - startedAt);
    }
}
