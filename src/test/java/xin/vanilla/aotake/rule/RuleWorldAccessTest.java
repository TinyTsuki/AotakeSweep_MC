package xin.vanilla.aotake.rule;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.Holder;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.api.rule.WorldAccess;
import xin.vanilla.aotake.internal.fabric.rule.FabricEntityAccess;
import xin.vanilla.aotake.internal.fabric.rule.FabricWorldAccess;
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
        FabricEntityAccess access = new FabricEntityAccess(entity, () -> {
            if (!active.get()) throw new IllegalStateException("Inactive view");
        });
        Field world = FabricEntityAccess.class.getDeclaredField("world");
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
        RuleTestItemEntity first = new RuleTestItemEntity(null);
        RuleTestItemEntity second = new RuleTestItemEntity(null);
        Level before = new UnloadedWorld(), after = new UnloadedWorld();
        first.moveToWorld(before);
        second.moveToWorld(after);
        AtomicReference<Entity> current = new AtomicReference<>(first);
        AtomicBoolean active = new AtomicBoolean(true);
        FabricEntityAccess access = new FabricEntityAccess(current::get, () -> {
            if (!active.get()) throw new IllegalStateException("Inactive cursor");
        });
        WorldAccess world = access.world();
        assertSame(before, world.nativeWorld(Level.class));
        first.moveToWorld(after);
        assertSame(after, world.nativeWorld(Level.class));
        first.moveToWorld(before);
        current.set(second);
        assertSame(world, access.world());
        assertSame(after, world.nativeWorld(Level.class));
        active.set(false);
        assertThrows(IllegalStateException.class, () -> world.nativeWorld(Level.class));
    }

    @Test public void unloadedBlocksAndEntityQueriesNeverRequestChunkCreation() throws Exception {
        UnloadedWorld world = new UnloadedWorld();
        WorldAccess access = new FabricWorldAccess(() -> world, () -> { });
        assertSame(world, access.nativeWorld(Level.class));
        assertEquals("minecraft:overworld", access.dimensionId());
        assertFalse(access.block(320, 64, 320).isPresent());
        assertTrue(access.entities(320, 0, 320, 336, 256, 336).isEmpty());
        assertTrue(world.chunks.requests > 0);
        int requests = world.chunks.requests;
        assertFalse(access.block(320, world.getMinBuildHeight() - 1, 320).isPresent());
        assertEquals(requests, world.chunks.requests);
        assertThrows(UnsupportedOperationException.class, () -> access.players().clear());
    }

    @Test public void invalidAndOverflowingBoundsAreRejectedBeforeQueryingTheWorld() {
        WorldAccess access = new FabricWorldAccess(() -> { throw new AssertionError("Level queried"); }, () -> { });
        assertThrows(IllegalArgumentException.class, () -> access.entities(Double.NaN, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(1, 0, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> access.entities(0, 0, 0, Double.MAX_VALUE, 1, 1));
    }

    private static final class UnloadedChunks extends ChunkSource {
        int requests;
        @Override public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
            assertFalse("Rule query attempted to load a chunk", create);
            requests++;
            return null;
        }
        @Override public int getLoadedChunksCount() { return 0; }
        @Override public void tick(java.util.function.BooleanSupplier time, boolean tickChunks) { }
        @Override public String gatherStats() { return "empty"; }
        @Override public LevelLightEngine getLightEngine() { throw new UnsupportedOperationException(); }
        @Override public BlockGetter getLevel() { throw new UnsupportedOperationException(); }
    }

    private static final class UnloadedWorld extends Level {
        final UnloadedChunks chunks = new UnloadedChunks();
        UnloadedWorld() throws Exception { super(null, Level.OVERWORLD, xin.vanilla.aotake.rule.RuleTestRegistries.access(), overworldHolder(), () -> InactiveProfiler.INSTANCE, false, false, 0, 0); }
        private static Holder<DimensionType> overworldHolder() throws Exception {
            net.minecraft.core.MappedRegistry<DimensionType> registry = new net.minecraft.core.MappedRegistry<>(net.minecraft.core.registries.Registries.DIMENSION_TYPE, com.mojang.serialization.Lifecycle.stable());
            Holder<DimensionType> holder = registry.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD, overworldType(), com.mojang.serialization.Lifecycle.stable());
            registry.freeze();
            return holder;
        }
        private static DimensionType overworldType() throws Exception {
            return new DimensionType(OptionalLong.empty(), true, false, false, true,
                    1D, false, true, 0, 256, 256, net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
                    net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD_EFFECTS, 0F,
                    new DimensionType.MonsterSettings(false, false, net.minecraft.util.valueproviders.ConstantInt.of(0), 0));
        }
        @Override public ChunkSource getChunkSource() { return chunks; }
        @Override public List<? extends Player> players() { return Collections.emptyList(); }
        @Override public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) { throw new UnsupportedOperationException(); }
        @Override public void playSeededSound(Player p, double x, double y, double z, Holder<SoundEvent> e, SoundSource c, float v, float pitch, long seed) { throw new UnsupportedOperationException(); }
        @Override public void playSeededSound(Player p, Entity entity, Holder<SoundEvent> e, SoundSource c, float v, float pitch, long seed) { throw new UnsupportedOperationException(); }
        @Override public Entity getEntity(int id) { return null; }
        @Override public MapItemSavedData getMapData(String id) { throw new UnsupportedOperationException(); }
        @Override public void setMapData(String id, MapItemSavedData data) { throw new UnsupportedOperationException(); }
        @Override public int getFreeMapId() { throw new UnsupportedOperationException(); }
        @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { throw new UnsupportedOperationException(); }
        @Override public Scoreboard getScoreboard() { throw new UnsupportedOperationException(); }
        @Override public RecipeManager getRecipeManager() { throw new UnsupportedOperationException(); }
        @Override public LevelTickAccess<Block> getBlockTicks() { throw new UnsupportedOperationException(); }
        @Override public LevelTickAccess<Fluid> getFluidTicks() { throw new UnsupportedOperationException(); }
        @Override public void levelEvent(Player player, int id, BlockPos pos, int data) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { return xin.vanilla.aotake.rule.RuleTestRegistries.access(); }
        @Override protected LevelEntityGetter<Entity> getEntities() {
            return (LevelEntityGetter<Entity>) java.lang.reflect.Proxy.newProxyInstance(LevelEntityGetter.class.getClassLoader(),
                    new Class<?>[]{LevelEntityGetter.class}, (proxy, method, args) -> method.getName().equals("getAll") ? Collections.emptyList() : null);
        }
        @Override public void gameEvent(net.minecraft.world.level.gameevent.GameEvent event, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.level.gameevent.GameEvent.Context context) { }
        @Override public net.minecraft.core.Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.VANILLA_SET; }
        @Override public String gatherChunkSourceStats() { return "empty-test"; }
        @Override public long nextSubTickCount() { return 0; }
        @Override public float getShade(Direction direction, boolean shade) { throw new UnsupportedOperationException(); }
    }
}
