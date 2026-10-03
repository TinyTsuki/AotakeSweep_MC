package xin.vanilla.aotake.rule;

/**
 * Offset is in the original configuration entry, including its declarations.
 */
public final class RuleDiagnostic extends IllegalArgumentException {
    private final int offset;

    public RuleDiagnostic(int offset, String reason) {
        super("At offset " + offset + ": " + reason);
        this.offset = offset;
    }

    public int offset() {
        return offset;
    }
}
