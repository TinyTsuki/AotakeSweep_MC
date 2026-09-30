package xin.vanilla.aotake.api.rule;

/** Prepared member functions. Implementations must not look up members during invocation. */
public interface NativeMembers {
    <F> F require(String id, Class<F> functionType);
    NativeMembers EMPTY = new NativeMembers() {
        @Override public <F> F require(String id, Class<F> type) {
            throw new IllegalArgumentException("No prepared native member: " + id);
        }
    };
}
