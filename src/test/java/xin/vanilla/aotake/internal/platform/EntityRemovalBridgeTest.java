package xin.vanilla.aotake.internal.platform;

import net.minecraft.server.Bootstrap;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.TextComponent;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;
import xin.vanilla.aotake.util.EntitySweeper;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;

public class EntityRemovalBridgeTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void terminalStatePrecedesNativeScoreboardCleanup() throws Exception {
        TestWorld world = world();
        ItemEntity entity = entity(world);
        Objective objective = world.scoreboard.addObjective("sweep", ObjectiveCriteria.DUMMY,
                new TextComponent("sweep"), ObjectiveCriteria.RenderType.INTEGER);
        world.scoreboard.getOrCreatePlayerScore(entity.getStringUUID(), objective).setScore(7);
        EntityRemovalBridge.discard(world, entity, false);
        assertFalse(entity.isAlive());
        assertNull(world.indexed);
        assertFalse("Discarded entities must not retain scores", world.scoreboard.hasPlayerScore(entity.getStringUUID(), objective));
    }

    @Test public void failedDetachmentRetriesAnAlreadyMarkedEntity() throws Exception {
        TestWorld world = world();
        ItemEntity entity = entity(world);
        world.failNext = true;
        assertTrue(EntitySweeper.tryScheduleRemoveEntity(entity, false));
        try {
            EntitySweeper.flushPendingRemovals(world);
            assertTrue("Failed native removal keeps ownership", EntitySweeper.isPendingRemoval(entity));
            EntitySweeper.flushPendingRemovals(world);
            assertEquals(2, world.attempts);
            assertNull("The retry must detach world tracking", world.indexed);
            assertFalse(EntitySweeper.isPendingRemoval(entity));
        } finally {
            world.failNext = false;
            EntitySweeper.flushPendingRemovals(world);
        }
    }

    private static TestWorld world() throws Exception {
        // Bypass native server construction; all exercised native hooks are explicit below.
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        TestWorld world = (TestWorld) ((Unsafe) field.get(null)).allocateInstance(TestWorld.class);
        world.scoreboard = new QuietScoreboard();
        return world;
    }

    private static ItemEntity entity(TestWorld world) {
        ItemEntity entity = new ItemEntity(EntityType.ITEM, null);
        entity.level = world;
        world.indexed = entity;
        return entity;
    }

    private static final class TestWorld extends ServerLevel {
        QuietScoreboard scoreboard;
        Entity indexed;
        int attempts;
        boolean failNext;
        private TestWorld() { super(null, null, null, null, null, null, null, null, false, 0L, Collections.emptyList(), false); }
        @Override public Entity getEntity(int id) { return indexed != null && indexed.getId() == id ? indexed : null; }
        @Override public ServerScoreboard getScoreboard() { return scoreboard; }
        @Override public void despawn(Entity entity) {
            attempts++;
            if (failNext) { failNext = false; throw new IllegalStateException("native detach failed"); }
            scoreboard.entityRemoved(entity);
            indexed = null;
        }
    }

    private static final class QuietScoreboard extends ServerScoreboard {
        QuietScoreboard() { super(null); }
        @Override public void onObjectiveAdded(Objective objective) { }
        @Override public void onScoreChanged(Score score) { }
        @Override public void onPlayerRemoved(String name) { }
        @Override public void onPlayerScoreRemoved(String name, Objective objective) { }
    }
}
