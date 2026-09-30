package xin.vanilla.aotake.api.sweep;

/** Immutable accounting snapshot, including side effects committed before a failure. */
public final class BatchResult {
    private final int size, processed, skipped, failed, committed, pendingRemoval;
    private final boolean pendingPersistence, cancelled;
    private final String failure;
    public BatchResult(int size, int processed, int skipped, int failed, int committed, int pendingRemoval,
                       boolean pendingPersistence, boolean cancelled, String failure) {
        if (size < 0 || processed < 0 || skipped < 0 || failed < 0 || committed < 0 || pendingRemoval < 0
                || (long) processed + skipped + failed > size) throw new IllegalArgumentException("Invalid batch accounting");
        this.size = size; this.processed = processed; this.skipped = skipped; this.failed = failed;
        this.committed = committed; this.pendingRemoval = pendingRemoval; this.pendingPersistence = pendingPersistence;
        this.cancelled = cancelled; this.failure = failure;
    }
    public int processed() { return processed; }
    public int skipped() { return skipped; }
    public int failed() { return failed; }
    public int committed() { return committed; }
    public int pendingRemoval() { return pendingRemoval; }
    public boolean pendingPersistence() { return pendingPersistence; }
    public boolean cancelled() { return cancelled; }
    public String failure() { return failure; }
    /** Failed, uncommitted entities remain unprocessed. */
    public int remaining() { return size - processed - skipped; }
}
