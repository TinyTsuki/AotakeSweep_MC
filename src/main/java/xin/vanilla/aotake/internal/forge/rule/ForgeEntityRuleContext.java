package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraftforge.common.UsernameCache;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.banira.common.util.*;
import java.util.*;

/** Build once for a rule plan, then reuse on the owning game thread. */
public final class ForgeEntityRuleContext extends LazyRuleContext<Entity> {
    private final RuleBindingPlan preparedPlan;
    private final Map<RuleBindingPlan.Binding, ForgeRuleBindings.Reader> readers = new IdentityHashMap<>();
    private Thread owner;
    public ForgeEntityRuleContext(RuleBindingPlan plan) {
        preparedPlan = plan;
        for (RuleBindingPlan.Binding b : plan.slots()) {
            if (b.kind().equals("nbt")) readers.put(b, ForgeRuleBindings.nbt(b));
            else if (b.kind().equals("field") || b.kind().equals("synced")) {
                if (b.payload().equals("entity.tick") || b.payload().equals("tickCount")) {
                    if (b.type() != Integer.class) throw new RuleDiagnostic(b.offset(), "Tick alias requires Integer");
                    readers.put(b, value -> ((Entity) value).tickCount);
                } else if (b.payload().equals("living.health")) {
                    if (b.type() != Float.class) throw new RuleDiagnostic(b.offset(), "Health alias requires Float");
                    readers.put(b, value -> value instanceof LivingEntity ? ((LivingEntity) value).getHealth() : null);
                } else readers.put(b, ForgeRuleBindings.field(b, Entity.class, getClass().getClassLoader()));
            }
        }
    }
    @Override protected void bindPlan(RuleBindingPlan plan) {
        if (plan != preparedPlan) throw new IllegalArgumentException("Context belongs to another binding plan");
        if (owner == null) owner = Thread.currentThread();
        if (owner != Thread.currentThread()) throw new IllegalStateException("Entity reads must stay on the owning thread");
    }
    @Override protected Object read(RuleBindingPlan.Binding b) {
        checkAccess();
        if (b.kind().equals("builtin")) return builtinValue(subject(), b.payload());
        ForgeRuleBindings.Reader reader = readers.get(b);
        if (reader == null) throw new IllegalStateException("Unprepared binding: " + b.name());
        return reader.read(b.kind().equals("nbt") ? subject().getPersistentData() : subject());
    }
    @Override protected void checkAccess() {
        if (Thread.currentThread() != owner || subject() == null) throw new IllegalStateException("Entity read outside evaluation");
    }
    private static Object builtinValue(Entity entity, String key) {
        switch (key) {
            case "resource": return EntityUtils.getEntityRegistryString(entity);
            case "namespace": return entity.getType().getRegistryName().getNamespace();
            case "path": return entity.getType().getRegistryName().getPath();
            case "itemResource": return entity instanceof ItemEntity ? ItemUtils.getItemRegistryString(((ItemEntity) entity).getItem()) : null;
            case "emptyItem": return entity instanceof ItemEntity && ((ItemEntity) entity).getItem().isEmpty();
            case "isItem": return entity instanceof ItemEntity;
            case "clazz": return entity.getClass(); case "itemClazz": return ItemEntity.class;
            case "clazzString": return entity.getClass().getName(); case "itemClazzString": return ItemEntity.class.getName();
            case "name": return entity.getName().getString(); case "displayName": return entity.getDisplayName().getString();
            case "customName": return entity.getCustomName() == null ? null : entity.getCustomName().getString();
            case "tick": return entity.tickCount;
            case "num": return entity instanceof ItemEntity ? ((ItemEntity) entity).getItem().getCount() : 1;
            case "dimension": case "dim": return DimensionUtils.getDimensionId(entity.level);
            case "x": return entity.getX(); case "y": return entity.getY(); case "z": return entity.getZ();
            case "chunkX": return net.minecraft.util.math.MathHelper.floor(entity.getX()) >> 4;
            case "chunkZ": return net.minecraft.util.math.MathHelper.floor(entity.getZ()) >> 4;
            case "hasOwner": return entity instanceof TameableEntity && ((TameableEntity) entity).getOwnerUUID() != null;
            case "ownerName": {
                UUID owner = entity instanceof TameableEntity ? ((TameableEntity) entity).getOwnerUUID() : null;
                return owner == null ? null : UsernameCache.getLastKnownUsername(owner);
            }
            default: throw new IllegalStateException("Unknown built-in binding: " + key);
        }
    }
}
