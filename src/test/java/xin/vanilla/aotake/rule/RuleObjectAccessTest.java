package xin.vanilla.aotake.rule;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.TextComponent;
import net.fabricmc.loader.api.MappingResolver;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.internal.fabric.rule.FabricEntityRuleContext;
import xin.vanilla.aotake.internal.fabric.rule.FabricNativeMembers;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.internal.fabric.platform.FabricBaniraRegistryService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import java.lang.reflect.Proxy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class RuleObjectAccessTest {
    @Test public void nbtIsSerializedLazilyOncePerEvaluationAndReleasedAfterUse() throws Exception {
        PlainEntity entity = new PlainEntity();
        RuleBindingPlan plan = RuleBindingPlan.defaults().resolved(Arrays.asList(
                new RuleBindingPlan.Binding("a", "nbt", "fixture.a", Integer.class, true, 0),
                new RuleBindingPlan.Binding("b", "nbt", "fixture.b", Integer.class, true, 0)));
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        assertTrue(context.evaluate(c -> c.nativeEntity(Entity.class) == entity, entity, plan));
        assertEquals(0, entity.serializations);
        assertTrue(context.evaluate(c -> {
            assertEquals(Integer.valueOf(1), c.value(0, Integer.class));
            entity.savedValue = 5;
            assertEquals(Integer.valueOf(2), c.value(1, Integer.class));
            return true;
        }, entity, plan));
        assertEquals(1, entity.serializations);
        java.lang.reflect.Field cache = FabricEntityRuleContext.class.getDeclaredField("entityNbt");
        cache.setAccessible(true);
        assertNull(cache.get(context));
        assertTrue(context.evaluate(c -> Integer.valueOf(5).equals(c.value(0, Integer.class)), entity, plan));
        assertEquals(2, entity.serializations);
        assertNull(cache.get(context));
    }
    @Test public void resourceMatchingUsesNativePartsAndExpiresWithTheEvaluation() {
        RuleBindingPlan plan = RuleBindingPlan.defaults();
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        ItemEntity item = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.APPLE));
        assertTrue(context.evaluate(c -> {
            assertTrue(c.matchesResource("minecraft", "item"));
            assertTrue(c.matchesResource("*", "item"));
            assertTrue(c.matchesResource("minecraft", "*"));
            assertTrue(c.matchesResource("*", "*"));
            assertFalse(c.matchesResource("other", "item"));
            assertFalse(c.matchesResource("minecraft", "items"));
            return true;
        }, item, plan));
        assertThrows(IllegalStateException.class, () -> context.matchesResource("*", "*"));
        assertTrue(context.evaluateResource(c -> { throw new AssertionError("Native ID used a text evaluation frame"); },
                item, plan, "minecraft", "item"));
        assertFalse(context.evaluateResource(c -> { throw new AssertionError("Native ID used a text evaluation frame"); },
                item, plan, "minecraft", "items"));
        assertThrows(IllegalStateException.class, context::resourceId);
    }
    @BeforeClass public static void bootstrap() {
        Bootstrap.bootStrap();
        FabricBaniraRegistryService registry = FabricBaniraRegistryService.INSTANCE;
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(BaniraPlatform.class.getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registryService")) return registry;
                    throw new UnsupportedOperationException(method.toString());
                }));
    }

    @Test public void entityAndLegacyGettersShareTheEvaluationCache() {
        Entity entity = new PlainEntity();
        entity.tickCount = 10;
        entity.setCustomName(new TextComponent("before"));
        RuleBindingPlan plan = RuleBindingPlan.defaults();
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        assertTrue(context.evaluate(c -> {
            assertEquals(10, c.tick());
            assertEquals("before", c.entity().customName());
            entity.tickCount = 20;
            entity.setCustomName(new TextComponent("after"));
            assertEquals(10, c.entity().tick());
            assertEquals("before", c.customName());
            assertEquals(c.resourceId(), c.entity().resourceId());
            return true;
        }, entity, plan));
        assertTrue(context.evaluate(c -> c.entity().tick() == 20 && "after".equals(c.customName()), entity, plan));
    }

    @Test public void nativeIdentityAndItemAccessFollowTheActiveEvaluationOnly() throws Exception {
        ItemEntity item = new ItemEntity(null, 1, 2, 3, new ItemStack(Items.STONE, 7));
        RuleBindingPlan plan = RuleBindingPlan.defaults();
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        AtomicReference<EntityAccess> borrowed = new AtomicReference<>();
        assertTrue(context.evaluate(c -> {
            assertSame(item, c.nativeEntity(Entity.class));
            assertSame(item, c.entity().nativeEntity(ItemEntity.class));
            assertEquals(7, c.entity().itemCount());
            assertEquals("minecraft:stone", c.entity().itemResourceId());
            assertEquals(1D, c.entity().x(), 0D);
            borrowed.set(c.entity());
            assertThrows(IllegalArgumentException.class, () -> c.nativeEntity(String.class));
            try {
                CompletableFuture.runAsync(() -> c.entity().x()).get(5, TimeUnit.SECONDS);
                fail("Cross-thread access accepted");
            } catch (ExecutionException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
            catch (InterruptedException | TimeoutException error) { throw new AssertionError(error); }
            return true;
        }, item, plan));
        assertThrows(IllegalStateException.class, () -> context.nativeEntity(Entity.class));
        assertThrows(IllegalStateException.class, () -> borrowed.get().x());
    }

    @Test public void nonItemAccessHasNoFabricatedItemAndNestedCallsRestoreEntity() {
        Entity plain = new PlainEntity();
        ItemEntity item = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.STONE));
        RuleBindingPlan plan = RuleBindingPlan.defaults();
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        assertTrue(context.evaluate(c -> {
            assertNull(c.entity().itemResourceId());
            assertEquals(0, c.entity().itemCount());
            assertFalse(c.isItem());
            assertTrue(context.evaluate(inner -> inner.nativeEntity(Entity.class) == item, item, plan));
            assertSame(plain, c.entity().nativeEntity(Entity.class));
            return true;
        }, plain, plan));
    }

    @Test public void borrowedViewsCannotFollowAnotherEvaluationFrame() {
        Entity plain = new PlainEntity();
        ItemEntity item = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.STONE));
        RuleBindingPlan plan = RuleBindingPlan.defaults();
        FabricEntityRuleContext context = new FabricEntityRuleContext(plan);
        AtomicReference<EntityAccess> previous = new AtomicReference<>();
        AtomicReference<EntityAccess> nested = new AtomicReference<>();
        AtomicReference<WorldAccess> outerWorld = new AtomicReference<>();
        AtomicReference<ServerAccess> outerServer = new AtomicReference<>();
        assertTrue(context.evaluate(c -> {
            EntityAccess outer = c.entity();
            previous.set(outer); outerWorld.set(c.world()); outerServer.set(c.server());
            assertSame(outer, c.entity());
            assertTrue(context.evaluate(inner -> {
                nested.set(inner.entity());
                assertThrows(IllegalStateException.class, () -> outer.nativeEntity(Entity.class));
                assertThrows(IllegalStateException.class, outer::tick);
                assertThrows(IllegalStateException.class, outer::customName);
                assertThrows(IllegalStateException.class, outer::resourceId);
                assertThrows(IllegalStateException.class, () -> outerWorld.get().nativeWorld(Object.class));
                assertThrows(IllegalStateException.class, () -> outerServer.get().nativeServer(Object.class));
                assertSame(item, inner.entity().nativeEntity(Entity.class));
                return true;
            }, item, plan));
            assertSame(plain, outer.nativeEntity(Entity.class));
            assertThrows(IllegalStateException.class, () -> nested.get().nativeEntity(Entity.class));
            return true;
        }, plain, plan));
        assertTrue(context.evaluate(c -> {
            assertThrows(IllegalStateException.class, () -> previous.get().nativeEntity(Entity.class));
            assertThrows(IllegalStateException.class, () -> nested.get().tick());
            return c.nativeEntity(Entity.class) == item;
        }, item, plan));
    }

    @Test public void externalProbeCompilesWithNativeInterfacesAndPreparedMinecraftMembers() throws Exception {
        StringBuilder source = new StringBuilder();
        try (Reader reader = resource("NativeEntityProbe.java")) {
            char[] buffer = new char[1024]; int n;
            while ((n = reader.read(buffer)) != -1) source.append(buffer, 0, n);
        }
        try (ScriptSession<EntityRule> session = BaniraScripts.open("aotake_sweep", EntityRule.class,
                "1", ScriptLimits.defaults(), Runnable::run)) {
            EntityRule rule = session.prepare(Collections.singletonList(new ScriptSource("native-probe",
                    "xin.vanilla.banira.generated.NativeEntityProbe", "NativeEntityProbe.java", source.toString())))
                    .get(10, TimeUnit.SECONDS).scripts().get("native-probe");
            List<NativeMemberSpec> specs = new ArrayList<>();
            ClassLoader loader = rule.getClass().getClassLoader();
            try (Reader reader = resource("NativeEntityProbe.members.json")) {
                for (JsonElement entry : new Gson().fromJson(reader, JsonArray.class)) {
                    JsonObject value = entry.getAsJsonObject();
                    specs.add(new NativeMemberSpec(value.get("id").getAsString(),
                            NativeMemberSpec.Kind.valueOf(value.get("kind").getAsString()),
                            value.get("owner").getAsString(), value.get("name").getAsString(),
                            value.get("descriptor").getAsString(),
                            Class.forName(value.get("functionType").getAsString(), false, loader)));
                }
            }
            // The isolated compiler fixture maps its declared test members explicitly.
            MappingResolver mappings = (MappingResolver) Proxy.newProxyInstance(MappingResolver.class.getClassLoader(),
                    new Class<?>[]{MappingResolver.class}, (proxy, method, args) -> {
                        assertEquals("intermediary", args[0]);
                        assertEquals("net.minecraft.class_1297", args[1]);
                        if (method.getName().equals("mapClassName")) return Entity.class.getName();
                        if (args[2].equals("method_23317")) return "getX";
                        if (args[2].equals("field_6012")) return "tickCount";
                        if (args[2].equals("method_5814")) return "setPos";
                        throw new AssertionError("Unexpected member: " + args[2]);
                    });
            NativeMembers members = FabricNativeMembers.prepare(specs, loader, mappings);
            ItemEntity entity = new ItemEntity(null, 42, 64, -2, new ItemStack(Items.STONE));
            entity.tickCount = 19;
            RuleBindingPlan plan = RuleBindingPlan.defaults();
            assertTrue(new FabricEntityRuleContext(plan, members).evaluate(rule, entity, plan));
            System.setProperty("aotake.test.moveOnSecondRead", "true");
            try {
                FabricEntityRuleContext context = new FabricEntityRuleContext(plan, members);
                assertFalse(context.evaluate(rule, entity, plan));
                assertEquals(42D, entity.getX(), 0D);
                assertFalse(context.evaluate(rule, entity, plan));
                assertEquals(80D, entity.getX(), 0D);
            } finally { System.clearProperty("aotake.test.moveOnSecondRead"); }
        }
    }

    private Reader resource(String file) {
        return new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/rules/" + file)),
                StandardCharsets.UTF_8);
    }

    private static final class PlainEntity extends Entity {
        int serializations;
        int savedValue = 1;
        PlainEntity() { super(EntityType.ARMOR_STAND, null); }
        @Override protected void defineSynchedData() { }
        @Override protected void readAdditionalSaveData(CompoundTag tag) { }
        @Override protected void addAdditionalSaveData(CompoundTag tag) {
            serializations++;
            CompoundTag values = new CompoundTag();
            values.putInt("a", savedValue);
            values.putInt("b", savedValue + 1);
            tag.put("fixture", values);
        }
        @Override public Packet<?> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    }
}
