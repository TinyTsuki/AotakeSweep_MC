package xin.vanilla.aotake.internal.fabric.rule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.resources.ResourceLocation;
import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.NativeMembers;
import xin.vanilla.aotake.api.rule.ServerAccess;
import xin.vanilla.aotake.rule.LazyRuleContext;
import xin.vanilla.aotake.rule.RuleBindingPlan;
import xin.vanilla.aotake.rule.RuleDiagnostic;
import xin.vanilla.banira.common.util.DimensionUtils;
import xin.vanilla.banira.common.util.EntityUtils;
import xin.vanilla.banira.common.util.ItemUtils;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Build once for a rule plan, then reuse on the owning game thread.
 */
public final class FabricEntityRuleContext extends LazyRuleContext<Entity> {
    private final RuleBindingPlan preparedPlan;
    private final Map<RuleBindingPlan.Binding, FabricRuleBindings.Reader> readers = new IdentityHashMap<>();
    private Thread owner;
    private long viewsFrame;
    private EntityAccess entityView;
    private ServerAccess serverView;
    private final NativeMembers members;
    private long nbtFrame;
    private CompoundTag entityNbt;

    public FabricEntityRuleContext(RuleBindingPlan plan) {
        this(plan, NativeMembers.EMPTY);
    }

    public FabricEntityRuleContext(RuleBindingPlan plan, NativeMembers members) {
        preparedPlan = plan;
        this.members = Objects.requireNonNull(members);
        for (RuleBindingPlan.Binding b : plan.slots()) {
            if (b.kind().equals("nbt")) readers.put(b, FabricRuleBindings.nbt(b));
            else if (b.kind().equals("field") || b.kind().equals("synced")) {
                if (b.payload().equals("entity.tick") || b.payload().equals("tickCount")) {
                    if (b.type() != Integer.class) throw new RuleDiagnostic(b.offset(), "Tick alias requires Integer");
                    readers.put(b, value -> ((Entity) value).tickCount);
                } else if (b.payload().equals("living.health")) {
                    if (b.type() != Float.class) throw new RuleDiagnostic(b.offset(), "Health alias requires Float");
                    readers.put(b, value -> value instanceof LivingEntity ? ((LivingEntity) value).getHealth() : null);
                } else readers.put(b, FabricRuleBindings.field(b, Entity.class, getClass().getClassLoader()));
            }
        }
    }

    @Override
    public EntityAccess entity() {
        prepareViews();
        if (entityView == null) {
            Runnable guard = frameGuard();
            entityView = new FabricEntityAccess(this::subject, guard) {
                @Override
                public String resourceId() {
                    guard.run();
                    return FabricEntityRuleContext.this.resourceId();
                }

                @Override
                public int tick() {
                    guard.run();
                    return FabricEntityRuleContext.this.tick();
                }

                @Override
                public String customName() {
                    guard.run();
                    return FabricEntityRuleContext.this.customName();
                }
            };
        }
        return entityView;
    }

    @Override
    public ServerAccess server() {
        prepareViews();
        if (serverView == null) serverView = new FabricServerAccess(() -> subject().getServer(), frameGuard());
        return serverView;
    }

    @Override
    public NativeMembers members() {
        checkAccess();
        return members;
    }

    @Override
    public boolean matchesResource(String namespace, String path) {
        checkAccess();
        ResourceLocation id = EntityUtils.getEntityRegistry(subject());
        if (id == null) return super.matchesResource(namespace, path);
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || path.equals(id.getPath()));
    }

    @Override
    protected boolean evaluateResourceValue(EntityRule fallback, Entity subject, RuleBindingPlan plan,
                                            String namespace, String path) {
        ResourceLocation id = EntityUtils.getEntityRegistry(subject);
        if (id == null) return super.evaluateResourceValue(fallback, subject, plan, namespace, path);
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || path.equals(id.getPath()));
    }

    @Override
    protected Class<?> resourceMatchKeyType() {
        return ResourceLocation.class;
    }

    @Override
    protected Object readResourceMatchKey(Entity subject) {
        return EntityUtils.getEntityRegistry(subject);
    }

    @Override
    protected boolean evaluateResourceValue(EntityRule fallback, Entity subject, RuleBindingPlan plan,
                                            String namespace, String path, Object key) {
        if (key == null) return super.evaluateResourceValue(fallback, subject, plan, namespace, path);
        ResourceLocation id = (ResourceLocation) key;
        return (namespace.equals("*") || namespace.equals(id.getNamespace()))
                && (path.equals("*") || path.equals(id.getPath()));
    }

    private void prepareViews() {
        checkAccess();
        if (viewsFrame != evaluationFrame()) {
            viewsFrame = evaluationFrame();
            entityView = null;
            serverView = null;
        }
    }

    private Runnable frameGuard() {
        long expected = evaluationFrame();
        return () -> {
            checkAccess();
            if (evaluationFrame() != expected)
                throw new IllegalStateException("Object view belongs to another evaluation");
        };
    }

    @Override
    protected void bindPlan(RuleBindingPlan plan) {
        if (plan != preparedPlan) throw new IllegalArgumentException("Context belongs to another binding plan");
        if (owner == null) owner = Thread.currentThread();
        if (owner != Thread.currentThread())
            throw new IllegalStateException("Entity reads must stay on the owning thread");
    }

    @Override
    protected Object read(RuleBindingPlan.Binding b) {
        checkAccess();
        if (b.kind().equals("builtin")) return builtinValue(subject(), b.payload());
        FabricRuleBindings.Reader reader = readers.get(b);
        if (reader == null) throw new IllegalStateException("Unprepared binding: " + b.name());
        return reader.read(b.kind().equals("nbt") ? entityNbt() : subject());
    }

    private CompoundTag entityNbt() {
        if (entityNbt == null || nbtFrame != evaluationFrame()) {
            entityNbt = subject().saveWithoutId(new CompoundTag());
            nbtFrame = evaluationFrame();
        }
        return entityNbt;
    }

    @Override
    public void clear() {
        super.clear();
        entityNbt = null;
        nbtFrame = 0;
    }

    @Override
    protected void checkAccess() {
        if (Thread.currentThread() != owner || subject() == null)
            throw new IllegalStateException("Entity read outside evaluation");
    }

    private static Object builtinValue(Entity entity, String key) {
        switch (key) {
            case "resource":
                return EntityUtils.getEntityRegistryString(entity);
            case "namespace":
                return EntityUtils.getEntityRegistry(entity).getNamespace();
            case "path":
                return EntityUtils.getEntityRegistry(entity).getPath();
            case "itemResource":
                return entity instanceof ItemEntity ? ItemUtils.getItemRegistryString(((ItemEntity) entity).getItem()) : null;
            case "emptyItem":
                return entity instanceof ItemEntity && ((ItemEntity) entity).getItem().isEmpty();
            case "isItem":
                return entity instanceof ItemEntity;
            case "clazz":
                return entity.getClass();
            case "itemClazz":
                return ItemEntity.class;
            case "clazzString":
                return entity.getClass().getName();
            case "itemClazzString":
                return ItemEntity.class.getName();
            case "name":
                return entity.getName().getString();
            case "displayName":
                return entity.getDisplayName().getString();
            case "customName":
                return entity.getCustomName() == null ? null : entity.getCustomName().getString();
            case "tick":
                return entity.tickCount;
            case "num":
                return entity instanceof ItemEntity ? ((ItemEntity) entity).getItem().getCount() : 1;
            case "dimension":
            case "dim":
                return DimensionUtils.getDimensionId(entity.level);
            case "x":
                return entity.getX();
            case "y":
                return entity.getY();
            case "z":
                return entity.getZ();
            case "chunkX":
                return net.minecraft.util.Mth.floor(entity.getX()) >> 4;
            case "chunkZ":
                return net.minecraft.util.Mth.floor(entity.getZ()) >> 4;
            case "hasOwner":
                return entity instanceof TamableAnimal && ((TamableAnimal) entity).getOwnerUUID() != null;
            case "ownerName": {
                UUID owner = entity instanceof TamableAnimal ? ((TamableAnimal) entity).getOwnerUUID() : null;
                if (owner == null || entity.getServer() == null) return null;
                GameProfile profile = entity.getServer().getProfileCache().get(owner);
                return profile == null ? null : profile.getName();
            }
            default:
                throw new IllegalStateException("Unknown built-in binding: " + key);
        }
    }
}
