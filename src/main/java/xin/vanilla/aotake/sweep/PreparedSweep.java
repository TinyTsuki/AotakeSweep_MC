package xin.vanilla.aotake.sweep;

import java.util.UUID;

/** Engine handoff token. It starts the original task exactly once, even after a delay. */
public final class PreparedSweep {
    private final SweepTask<?> task;
    private final long generationId;
    private boolean consumed;
    PreparedSweep(SweepTask<?> task, long generationId) { this.task = task; this.generationId = generationId; }
    public UUID taskId() { return task.id(); }
    public long generationId() { return generationId; }
    public void start() {
        task.checkDriver();
        if (consumed) throw new IllegalStateException("Prepared sweep already consumed");
        consumed = true;
        task.start();
    }
}
