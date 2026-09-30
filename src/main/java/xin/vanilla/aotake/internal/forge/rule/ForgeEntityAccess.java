package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.banira.common.util.EntityUtils;
import xin.vanilla.banira.common.util.ItemUtils;
import java.util.*;
import java.util.function.Supplier;

public class ForgeEntityAccess implements EntityAccess {
    private final Supplier<Entity> source;
    private final Runnable guard;
    private final WorldAccess world;

    /** A stable view for a task-owned candidate, with the task's lifetime guard. */
    public ForgeEntityAccess(Entity entity, Runnable guard) { this(() -> entity, guard); }

    /** A borrowed evaluation cursor; callers must not retain it outside the callback. */
    public ForgeEntityAccess(Supplier<Entity> source, Runnable guard) {
        this.source = Objects.requireNonNull(source); this.guard = Objects.requireNonNull(guard);
        this.world = new ForgeWorldAccess(() -> current().level, guard);
    }
    private Entity current() { guard.run(); return Objects.requireNonNull(source.get(), "entity"); }
    @Override public UUID uuid() { return current().getUUID(); }
    @Override public String resourceId() { return EntityUtils.getEntityRegistryString(current()); }
    @Override public double x() { return current().getX(); }
    @Override public double y() { return current().getY(); }
    @Override public double z() { return current().getZ(); }
    @Override public WorldAccess world() { current(); return world; }
    @Override public String name() { return current().getName().getString(); }
    @Override public String customName() {
        Entity entity = current();
        return entity.getCustomName() == null ? null : entity.getCustomName().getString();
    }
    @Override public boolean removed() { return current().removed; }
    @Override public boolean alive() { return current().isAlive(); }
    @Override public int tick() { return current().tickCount; }
    @Override public String itemResourceId() {
        Entity entity = current();
        return entity instanceof ItemEntity ? ItemUtils.getItemRegistryString(((ItemEntity) entity).getItem()) : null;
    }
    @Override public int itemCount() {
        Entity entity = current();
        return entity instanceof ItemEntity ? ((ItemEntity) entity).getItem().getCount() : 0;
    }
    @Override public <T> T nativeEntity(Class<T> type) { return cast(current(), type); }
    static <T> T cast(Object value, Class<T> type) {
        if (!type.isInstance(value)) throw new IllegalArgumentException("Native object is not " + type.getName());
        return type.cast(value);
    }
    static List<EntityAccess> views(Collection<? extends Entity> entities, Runnable guard) {
        List<EntityAccess> result = new ArrayList<>(entities.size());
        for (Entity entity : entities) result.add(new ForgeEntityAccess(entity, guard));
        return Collections.unmodifiableList(result);
    }
}
