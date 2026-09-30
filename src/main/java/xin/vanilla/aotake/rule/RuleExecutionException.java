package xin.vanilla.aotake.rule;

/** A failed rule is unavailable, never a successful non-match. */
public final class RuleExecutionException extends IllegalStateException {
    private final long generationId;

    public RuleExecutionException(long generationId, String message) {
        super(message);
        this.generationId = generationId;
    }

    public RuleExecutionException(long generationId, String message, Throwable cause) {
        super(message, cause);
        this.generationId = generationId;
    }

    public long generationId() { return generationId; }
}
