package xin.vanilla.aotake.rule;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.crafting.RecipeManager;
import net.minecraft.profiler.EmptyProfiler;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.tags.ITagCollectionSupplier;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.*;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.world.storage.MapData;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.WorldAccess;
import xin.vanilla.aotake.internal.forge.rule.ForgeEntityAccess;
import xin.vanilla.aotake.internal.forge.rule.ForgeWorldAccess;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class RuleWorldAccessTest {
    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void nativeOnlyAccessDefersWorldViewAndChecksGuardBeforeCreation() throws Exception {
        ItemEntity entity = new ItemEntity(null, 0, 0, 0);
        AtomicBoolean active = new AtomicBoolean(true);
        ForgeEntityAccess access = new ForgeEntityAccess(entity, () -> {
            if (!active.get()) throw new IllegalStateException("Inactive view");
        });
        Field world = ForgeEntityAccess.class.getDeclaredField("world");
        world.setAccessible(true);
        assertSame(entity, access.nativeEntity(Entity.class));
        assertNull("Native-only access must not allocate a world view", world.get(access));
        active.set(false);
        assertThrows(IllegalStateException.class, access::world);
        assertNull(world.get(access));
        active.set(true);
        WorldAccess view = access.world();
        assertSame(view, access.world());
        assertSame(view, world.get(access));
        active.set(false);
        assertThrows(IllegalStateException.class, access::world);
        assertThrows(IllegalStateException.class, () -> view.nativeWorld(World.class));
    }

    @Test public void cachedWorldViewFollowsLiveEntityAndBorrowedCursor() throws Exception {
        ItemEntity first = new ItemEntity(null, 0, 0, 0);
        ItemEntity second = new ItemEntity(null, 0, 0, 0);
        World before = new UnloadedWorld(), after = new UnloadedWorld();
        first.level = before;
        second.level = after;
        AtomicReference<Entity> current = new AtomicReference<>(first);
        AtomicBoolean active = new AtomicBoolean(true);
        ForgeEntityAccess access = new ForgeEntityAccess(current::get, () -> {
            if (!active.get()) throw new IllegalStateException("Inactive cursor");
        });
        WorldAccess world = access.world();
        assertSame(before, world.nativeWorld(World.class));
        first.level = after;
        assertSame(after, world.nativeWorld(World.class));
        first.level = before;
        current.set(second);
        assertSame(world, access.world());
        assertSame(after, world.nativeWorld(World.class));
        active.set(false);
        assertThrows(IllegalStateException.class, () -> world.nativeWorld(World.class));
    }

    @Test public void unloadedBlocksAndEntityQueriesNeverRequestChunkCreation() throws Exception {
        UnloadedWorld world = new UnloadedWorld();
        WorldAccess access = new ForgeWorldAccess(() -> world, () -> { });
        assertSame(world, access.nativeWorld(World.class));
        assertEquals("minecraft:overworld", access.dimensionId());
        assertFalse(access.block(320, 64, 320).isPresent());
        assertTrue(access.entities(320, 0, 320, 336, 256, 336).isEmpty());
        assertTrue(world.chunks.requests > 0);
        int requests = world.chunks.requests;
        assertFalse(access.block(320, -1, 320).isPresent());
        assertEquals(requests, world.chunks.requests);
        assertThrows(UnsupportedOperationException.class, () -> access.players().clear());
    }

    @Test public void invalidAndOverflowingBoundsAreRejectedBeforeQueryingTheWorld() {
        WorldAccess access = new ForgeWorldAccess(() -> { throw new AssertionError("World queried"); }, () -> { });
        assertThrows(IllegalArgumentException.class, () -> access.entities(Double.NaN, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(1, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(0, 0, 0, Double.MAX_VALUE, 1, 1));
    }

    private static final class UnloadedChunks extends AbstractChunkProvider {
        int requests;
        @Override public IChunk getChunk(int x, int z, ChunkStatus status, boolean create) {
            assertFalse("Rule query attempted to load a chunk", create);
            requests++;
            return null;
        }
        @Override public String gatherStats() { return "empty"; }
        @Override public WorldLightManager getLightEngine() { throw new UnsupportedOperationException(); }
        @Override public IBlockReader getLevel() { throw new UnsupportedOperationException(); }
    }

    private static final class UnloadedWorld extends World {
        final UnloadedChunks chunks = new UnloadedChunks();
        UnloadedWorld() throws Exception { super(null, World.OVERWORLD, overworldType(), () -> EmptyProfiler.INSTANCE, false, false, 0); }
        private static DimensionType overworldType() throws Exception {
            Field field = DimensionType.class.getDeclaredField("DEFAULT_OVERWORLD");
            field.setAccessible(true);
            return (DimensionType) field.get(null);
        }
        @Override public AbstractChunkProvider getChunkSource() { return chunks; }
        @Override public List<? extends PlayerEntity> players() { return Collections.emptyList(); }
        @Override public Collection<net.minecraftforge.entity.PartEntity<?>> getPartEntities() { return Collections.emptyList(); }
        @Override public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) { throw new UnsupportedOperationException(); }
        @Override public void playSound(PlayerEntity p, double x, double y, double z, SoundEvent e, SoundCategory c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public void playSound(PlayerEntity p, Entity entity, SoundEvent e, SoundCategory c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public Entity getEntity(int id) { return null; }
        @Override public MapData getMapData(String id) { throw new UnsupportedOperationException(); }
        @Override public void setMapData(MapData data) { throw new UnsupportedOperationException(); }
        @Override public int getFreeMapId() { throw new UnsupportedOperationException(); }
        @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { throw new UnsupportedOperationException(); }
        @Override public Scoreboard getScoreboard() { throw new UnsupportedOperationException(); }
        @Override public RecipeManager getRecipeManager() { throw new UnsupportedOperationException(); }
        @Override public ITagCollectionSupplier getTagManager() { throw new UnsupportedOperationException(); }
        @Override public ITickList<Block> getBlockTicks() { throw new UnsupportedOperationException(); }
        @Override public ITickList<Fluid> getLiquidTicks() { throw new UnsupportedOperationException(); }
        @Override public void levelEvent(PlayerEntity player, int id, BlockPos pos, int data) { throw new UnsupportedOperationException(); }
        @Override public Biome getUncachedNoiseBiome(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.util.registry.DynamicRegistries registryAccess() { throw new UnsupportedOperationException(); }
        @Override public float getShade(Direction direction, boolean shade) { throw new UnsupportedOperationException(); }
    }
}
