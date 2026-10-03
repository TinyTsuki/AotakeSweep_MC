package xin.vanilla.aotake.api.rule;

import java.util.UUID;

/**
 * Read-only entity view, valid within its owning evaluation or sweep task.
 */
public interface EntityAccess {
    UUID uuid();

    String resourceId();

    double x();

    double y();

    double z();

    WorldAccess world();

    String name();

    String customName();

    boolean removed();

    boolean alive();

    int tick();

    String itemResourceId();

    int itemCount();

    <T> T nativeEntity(Class<T> type);
}
