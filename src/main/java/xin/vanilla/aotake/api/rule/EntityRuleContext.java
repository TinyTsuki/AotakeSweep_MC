package xin.vanilla.aotake.api.rule;

/**
 * Valid only during the current entity evaluation, on its owning game thread.
 * Borrowed object views are suspended during nested callbacks and expire on return.
 */
public interface EntityRuleContext {
    String resourceId();

    /**
     * Matches literal registry parts or a whole-part wildcard.
     */
    default boolean matchesResource(String namespace, String path) {
        String resource = resourceId();
        int separator = resource.indexOf(':');
        if (separator < 1) throw new IllegalStateException("Invalid entity resource: " + resource);
        return (namespace.equals("*") || separator == namespace.length() && resource.regionMatches(0, namespace, 0, separator))
                && (path.equals("*") || resource.length() - separator - 1 == path.length()
                && resource.regionMatches(separator + 1, path, 0, path.length()));
    }

    int tick();

    String customName();

    boolean hasOwner();

    boolean isItem();

    <T> T value(int slot, Class<T> type);

    default EntityAccess entity() {
        throw new UnsupportedOperationException("Entity access is unavailable in this context");
    }

    default WorldAccess world() {
        return entity().world();
    }

    default ServerAccess server() {
        throw new UnsupportedOperationException("Server access is unavailable in this context");
    }

    default <T> T nativeEntity(Class<T> type) {
        return entity().nativeEntity(type);
    }

    default <T> T nativeWorld(Class<T> type) {
        return world().nativeWorld(type);
    }

    default <T> T nativeServer(Class<T> type) {
        return server().nativeServer(type);
    }

    default NativeMembers members() {
        return NativeMembers.EMPTY;
    }
}
