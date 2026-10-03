package xin.vanilla.aotake.rule;

/** A failed rule is unavailable, never a successful non-match. */
public final class RuleExecutionException extends IllegalStateException {
    private final long generationId;
    private final boolean unavailable;

    public RuleExecutionException(long generationId, String message) {
        this(generationId, message, null, false);
    }

    public RuleExecutionException(long generationId, String message, Throwable cause) {
        this(generationId, message, cause, false);
    }

    private RuleExecutionException(long generationId, String message, Throwable cause, boolean unavailable) {
        super(message, cause);
        this.generationId = generationId;
        this.unavailable = unavailable;
    }

    public long generationId() { return generationId; }
    public boolean isUnavailable() { return unavailable; }

    public static RuleExecutionException unavailable(long generationId, String reason) {
        return new RuleExecutionException(generationId, reason, null, true);
    }
}
