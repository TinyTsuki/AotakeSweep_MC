package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.sweep.SweepPhase;

import java.util.Objects;

/**
 * Tick-owned scheduling for the same prepared task throughout its delay and batches.
 */
public final class SweepExecution {
    private final SweepTask<?> task;
    private PreparedSweep prepared;
    private final int interval;
    private long due;

    public SweepExecution(SweepTask<?> task, PreparedSweep prepared, long tick, int delay, int interval, boolean scanOnly) {
        this.task = Objects.requireNonNull(task);
        this.prepared = Objects.requireNonNull(prepared);
        this.interval = Math.max(1, interval);
        due = tick + Math.max(0, delay);
        if (scanOnly) {
            prepared.completeScan("no cleanup requested");
            this.prepared = null;
        }
    }

    public void tick(long tick) {
        if (task.phase() != SweepPhase.READY && task.phase() != SweepPhase.RUNNING) {
            prepared = null;
            return;
        }
        if (tick < due) return;
        if (prepared != null) {
            PreparedSweep ready = prepared;
            prepared = null;
            ready.start();
        }
        if (task.phase() == SweepPhase.RUNNING) task.runNextBatch();
        due = tick + interval;
    }
}
