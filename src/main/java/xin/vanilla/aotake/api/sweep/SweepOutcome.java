package xin.vanilla.aotake.api.sweep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Terminal metadata contains no entity, policy, backend or exception references.
 */
public final class SweepOutcome {
    private final SweepPhase status, stoppedAt;
    private final String reason, failure;
    private final int scanned, selected, processed, skipped, failed, committed, pendingRemoval;
    private final boolean pendingPersistence;
    private final List<String> diagnostics;

    public SweepOutcome(SweepPhase status, SweepPhase stoppedAt, String reason, String failure,
                        int scanned, int selected, int processed, int skipped, int failed, int committed,
                        int pendingRemoval, boolean pendingPersistence, List<String> diagnostics) {
        this.status = status;
        this.stoppedAt = stoppedAt;
        this.reason = reason;
        this.failure = failure;
        this.scanned = scanned;
        this.selected = selected;
        this.processed = processed;
        this.skipped = skipped;
        this.failed = failed;
        this.committed = committed;
        this.pendingRemoval = pendingRemoval;
        this.pendingPersistence = pendingPersistence;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public SweepPhase status() {
        return status;
    }

    public SweepPhase stoppedAt() {
        return stoppedAt;
    }

    public String reason() {
        return reason;
    }

    public String failure() {
        return failure;
    }

    public int scanned() {
        return scanned;
    }

    public int selected() {
        return selected;
    }

    public int processed() {
        return processed;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public int committed() {
        return committed;
    }

    public int pendingRemoval() {
        return pendingRemoval;
    }

    public boolean pendingPersistence() {
        return pendingPersistence;
    }

    public int remaining() {
        return selected - processed - skipped;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }
}
