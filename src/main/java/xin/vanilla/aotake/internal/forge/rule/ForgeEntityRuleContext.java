package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraftforge.common.UsernameCache;
import net.minecraft.util.ResourceLocation;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.banira.common.util.*;
import java.util.*;

/** Build once for a rule plan, then reuse on the owning game thread. */
public final class ForgeEntityRuleContext extends LazyRuleContext<Entity> {
    private final RuleBindingPlan preparedPlan;
    private final Map<RuleBindingPlan.Binding, ForgeRuleBindings.Reader> readers = new IdentityHashMap<>();
    private Thread owner;
    private long viewsFrame;
    private EntityAccess entityView;
    private ServerAccess serverView;
    private final NativeMembers members;
    public ForgeEntityRuleContext(RuleBindingPlan plan) {
        this(plan, NativeMembers.EMPTY);
    }
    public ForgeEntityRuleContext(RuleBindingPlan plan, NativeMembers members) {
        preparedPlan = plan;
        this.members = Objects.requireNonNull(members);
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
    @Override public EntityAccess entity() {
        prepareViews();
        if (entityView == null) {
            Runnable guard = frameGuard();
            entityView = new ForgeEntityAccess(this::subject, guard) {
                @Override public String resourceId() { guard.run(); return ForgeEntityRuleContext.this.resourceId(); }
                @Override public int tick() { guard.run(); return ForgeEntityRuleContext.this.tick(); }
                @Override public String customName() { guard.run(); return ForgeEntityRuleContext.this.customName(); }
            };
        }
        return entityView;
    }
    @Override public ServerAccess server() {
        prepareViews();
        if (serverView == null) serverView = new ForgeServerAccess(() -> subject().getServer(), frameGuard());
        return serverView;
    }
    @Override public NativeMembers members() { checkAccess(); return members; }
    @Override public boolean matchesResource(String namespace, String path) {
        checkAccess();
        ResourceLocation id = EntityUtils.getEntityRegistry(subject());
        if (id == null) return super.matchesResource(namespace, path);
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || path.equals(id.getPath()));
    }
    @Override protected boolean evaluateResourceValue(EntityRule fallback, Entity subject, RuleBindingPlan plan,
                                                      String namespace, String path) {
        ResourceLocation id = EntityUtils.getEntityRegistry(subject);
        if (id == null) return super.evaluateResourceValue(fallback, subject, plan, namespace, path);
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || path.equals(id.getPath()));
    }
    private void prepareViews() {
        checkAccess();
        if (viewsFrame != evaluationFrame()) {
            viewsFrame = evaluationFrame();
            entityView = null; serverView = null;
        }
    }
    private Runnable frameGuard() {
        long expected = evaluationFrame();
        return () -> {
            checkAccess();
            if (evaluationFrame() != expected) throw new IllegalStateException("Object view belongs to another evaluation");
        };
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
