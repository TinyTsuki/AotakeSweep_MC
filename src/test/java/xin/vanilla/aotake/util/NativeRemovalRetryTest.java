package xin.vanilla.aotake.util;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public class NativeRemovalRetryTest {
    @BeforeClass public static void bootstrap() throws ReflectiveOperationException {
        xin.vanilla.aotake.rule.NativeTestRegistries.bootstrap();
    }

    @Test public void removedFlagDoesNotAcknowledgeAnInterruptedNativeCallback() throws Exception {
        retry(false);
    }

    @Test public void uuidTrackingRequiresRetryEvenAfterIdTrackingWasDetached() throws Exception {
        retry(true);
    }

    private static void retry(boolean detachIdFirst) throws Exception {
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        // The test exercises native entity callbacks without constructing a server or loading chunks.
        TestWorld world = (TestWorld) ((Unsafe) unsafe.get(null)).allocateInstance(TestWorld.class);
        ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
        Method setLevel = Entity.class.getDeclaredMethod("setLevel", Level.class);
        setLevel.setAccessible(true);
        setLevel.invoke(entity, world);
        world.byId = world.byUuid = entity;
        int[] attempts = {0};
        entity.setLevelCallback(new EntityInLevelCallback() {
            @Override public void onMove() { }
            @Override public void onRemove(Entity.RemovalReason reason) {
                if (++attempts[0] == 1) {
                    if (detachIdFirst) world.byId = null;
                    throw new IllegalStateException("interrupted native callback");
                }
                world.byId = world.byUuid = null;
            }
        });
        assertTrue(EntitySweeper.tryScheduleRemoveEntity(entity, false));
        try {
            EntitySweeper.flushPendingRemovals(world);
            assertFalse(entity.isAlive());
            assertTrue(EntitySweeper.isPendingRemoval(entity));
            assertSame(entity, world.getEntity(entity.getUUID()));
            EntitySweeper.flushPendingRemovals(world);
            assertEquals("Incomplete tracking must be retried", 2, attempts[0]);
            assertNull(world.getEntity(entity.getId()));
            assertNull(world.getEntity(entity.getUUID()));
            assertFalse(EntitySweeper.isPendingRemoval(entity));
        } finally {
            world.byId = world.byUuid = null;
            EntitySweeper.flushPendingRemovals(world);
        }
    }

    private static final class TestWorld extends ServerLevel {
        Entity byId, byUuid;
        private TestWorld() { super(null, null, null, null, null, null, null, false, 0L, Collections.emptyList(), false, null); }
        @Override public Entity getEntity(int id) { return byId != null && byId.getId() == id ? byId : null; }
        @Override public Entity getEntity(UUID uuid) { return byUuid != null && byUuid.getUUID().equals(uuid) ? byUuid : null; }
    }
}
