package xin.vanilla.aotake.rule;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.server.Bootstrap;
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
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test public void nativeOnlyAccessDefersWorldViewAndChecksGuardBeforeCreation() throws Exception {
        ItemEntity entity = new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, null);
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
        assertThrows(IllegalStateException.class, () -> view.nativeWorld(Level.class));
    }

    @Test public void cachedWorldViewFollowsLiveEntityAndBorrowedCursor() throws Exception {
        ItemEntity first = new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, null);
        ItemEntity second = new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, null);
        Level before = new UnloadedWorld(), after = new UnloadedWorld();
        first.level = before;
        second.level = after;
        AtomicReference<Entity> current = new AtomicReference<>(first);
        AtomicBoolean active = new AtomicBoolean(true);
        ForgeEntityAccess access = new ForgeEntityAccess(current::get, () -> {
            if (!active.get()) throw new IllegalStateException("Inactive cursor");
        });
        WorldAccess world = access.world();
        assertSame(before, world.nativeWorld(Level.class));
        first.level = after;
        assertSame(after, world.nativeWorld(Level.class));
        first.level = before;
        current.set(second);
        assertSame(world, access.world());
        assertSame(after, world.nativeWorld(Level.class));
        active.set(false);
        assertThrows(IllegalStateException.class, () -> world.nativeWorld(Level.class));
    }

    @Test public void unloadedBlocksAndEntityQueriesNeverRequestChunkCreation() throws Exception {
        UnloadedWorld world = new UnloadedWorld();
        WorldAccess access = new ForgeWorldAccess(() -> world, () -> { });
        assertSame(world, access.nativeWorld(Level.class));
        assertEquals("minecraft:overworld", access.dimensionId());
        assertFalse(access.block(320, 64, 320).isPresent());
        assertTrue(access.entities(320, 0, 320, 336, 256, 336).isEmpty());
        assertTrue(world.chunks.requests > 0);
        int requests = world.chunks.requests;
        assertFalse(access.block(320, -1000, 320).isPresent());
        assertEquals(requests, world.chunks.requests);
        assertThrows(UnsupportedOperationException.class, () -> access.players().clear());
    }

    @Test public void invalidAndOverflowingBoundsAreRejectedBeforeQueryingTheWorld() {
        WorldAccess access = new ForgeWorldAccess(() -> { throw new AssertionError("Level queried"); }, () -> { });
        assertThrows(IllegalArgumentException.class, () -> access.entities(Double.NaN, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(1, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(0, 0, 0, Double.MAX_VALUE, 1, 1));
    }

    private static final class UnloadedChunks extends net.minecraft.world.level.chunk.ChunkSource {
        int requests;
        @Override public net.minecraft.world.level.chunk.ChunkAccess getChunk(int x, int z, net.minecraft.world.level.chunk.ChunkStatus status, boolean create) {
            assertFalse("Rule query attempted to load a chunk", create);
            requests++;
            return null;
        }
        @Override public void tick(java.util.function.BooleanSupplier budget, boolean tickChunks) { }
        @Override public int getLoadedChunksCount() { return 0; }
        @Override public String gatherStats() { return "empty"; }
        @Override public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.level.BlockGetter getLevel() { throw new UnsupportedOperationException(); }
    }

    private static final class UnloadedWorld extends Level {
        final UnloadedChunks chunks = new UnloadedChunks();
        UnloadedWorld() throws Exception {
            super(null, Level.OVERWORLD, net.minecraft.core.Holder.direct(overworldType()),
                    () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE, false, false, 0);
        }
        private static net.minecraft.world.level.dimension.DimensionType overworldType() throws Exception {
            Field field = net.minecraft.world.level.dimension.DimensionType.class.getDeclaredField("DEFAULT_OVERWORLD");
            field.setAccessible(true);
            return (net.minecraft.world.level.dimension.DimensionType) field.get(null);
        }
        @Override public net.minecraft.world.level.chunk.ChunkSource getChunkSource() { return chunks; }
        @Override public List<? extends Player> players() { return Collections.emptyList(); }
        @Override public Collection<net.minecraftforge.entity.PartEntity<?>> getPartEntities() { return Collections.emptyList(); }
        @Override public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) { throw new UnsupportedOperationException(); }
        @Override public void playSound(Player p, double x, double y, double z, net.minecraft.sounds.SoundEvent e, net.minecraft.sounds.SoundSource c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public void playSound(Player p, Entity entity, net.minecraft.sounds.SoundEvent e, net.minecraft.sounds.SoundSource c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public Entity getEntity(int id) { return null; }
        @Override public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(String id) { throw new UnsupportedOperationException(); }
        @Override public void setMapData(String id, net.minecraft.world.level.saveddata.maps.MapItemSavedData data) { throw new UnsupportedOperationException(); }
        @Override public int getFreeMapId() { throw new UnsupportedOperationException(); }
        @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.scores.Scoreboard getScoreboard() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() { throw new UnsupportedOperationException(); }
        @Override public void levelEvent(Player player, int id, BlockPos pos, int data) { throw new UnsupportedOperationException(); }
        @Override public void gameEvent(Entity entity, net.minecraft.world.level.gameevent.GameEvent event, BlockPos pos) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
        @Override public float getShade(net.minecraft.core.Direction direction, boolean shade) { throw new UnsupportedOperationException(); }
        @Override public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) { throw new UnsupportedOperationException(); }
        @Override public String gatherChunkSourceStats() { return "empty"; }
        @Override public long nextSubTickCount() { return 0; }
        @SuppressWarnings("unchecked")
        @Override protected net.minecraft.world.level.entity.LevelEntityGetter<Entity> getEntities() {
            return (net.minecraft.world.level.entity.LevelEntityGetter<Entity>) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{net.minecraft.world.level.entity.LevelEntityGetter.class},
                    (proxy, method, args) -> method.getName().equals("getAll") ? Collections.emptyList() : null);
        }
    }
}
