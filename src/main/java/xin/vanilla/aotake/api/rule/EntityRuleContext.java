package xin.vanilla.aotake.api.rule;

import java.util.List;
import java.util.Map;

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

    /**
     * Reads a numeric value from entity persistent data without a legacy binding file.
     */
    default Integer persistentInt(String... path) {
        throw new UnsupportedOperationException("Persistent data is unavailable in this context");
    }

    /**
     * Reads literal compound keys or decimal indexes into lists and arrays.
     * Returns a Number, String, read-only List, read-only Map, or null for a missing path.
     * Container snapshots are detached and bounded; request a narrower path for large containers.
     * Forge and NeoForge read persistent data; Fabric reads serialized entity data for this evaluation.
     */
    default Object persistentValue(String... path) {
        return persistentValue(Object.class, path);
    }

    /**
     * Reads the requested Java type, or null for a missing path or a different type.
     * Boxed numbers keep their NBT type. Boolean accepts only Byte 0 or 1.
     */
    default <T> T persistentValue(Class<T> type, String... path) {
        throw new UnsupportedOperationException("Persistent data is unavailable in this context");
    }

    default boolean hasPersistentValue(String... path) {
        return persistentValue(path) != null;
    }

    default Number persistentNumber(String... path) {
        return persistentValue(Number.class, path);
    }

    default String persistentString(String... path) {
        return persistentValue(String.class, path);
    }

    default Boolean persistentBoolean(String... path) {
        return persistentValue(Boolean.class, path);
    }

    default List<?> persistentList(String... path) {
        return persistentValue(List.class, path);
    }

    default Map<?, ?> persistentCompound(String... path) {
        return persistentValue(Map.class, path);
    }

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
