package xin.vanilla.aotake.api.rule;

/** Valid only during the current entity evaluation, on its owning game thread. */
public interface EntityRuleContext {
    String resourceId();
    int tick();
    String customName();
    boolean hasOwner();
    boolean isItem();
    <T> T value(int slot, Class<T> type);
}
