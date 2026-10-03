package xin.vanilla.aotake.sweep;

import java.util.UUID;

/**
 * Engine handoff token. It starts the original task exactly once, even after a delay.
 */
public final class PreparedSweep {
    private final SweepTask<?> task;
    private final long generationId;
    private boolean consumed;

    PreparedSweep(SweepTask<?> task, long generationId) {
        this.task = task;
        this.generationId = generationId;
    }

    public UUID taskId() {
        return task.id();
    }

    public long generationId() {
        return generationId;
    }

    public int selectedCount() {
        return task.selectedCount();
    }

    public void start() {
        consume();
        task.start();
    }

    /**
     * Complete a chunk check without invoking cleanup hooks or an empty recycle batch.
     */
    public void completeScan(String reason) {
        if (reason == null) throw new NullPointerException("reason");
        consume();
        task.completeScan(reason);
    }

    private void consume() {
        task.checkDriver();
        if (consumed) throw new IllegalStateException("Prepared sweep already consumed");
        consumed = true;
    }
}
