package xin.vanilla.aotake.api.rule;

/**
 * Snapshot of a loaded block state, bound to the caller's access lifetime.
 */
public interface BlockAccess {
    String resourceId();

    boolean air();

    <T> T nativeState(Class<T> type);
}
