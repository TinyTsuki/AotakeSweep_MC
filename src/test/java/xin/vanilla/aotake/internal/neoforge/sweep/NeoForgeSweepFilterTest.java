package xin.vanilla.aotake.internal.neoforge.sweep;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.internal.neoforge.platform.NeoForgeBaniraRegistryService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.Assert.*;

public class NeoForgeSweepFilterTest {
    private static Field platformField;
    private static Object previousPlatform;
    @BeforeClass public static void bootstrap() throws Exception {
        xin.vanilla.aotake.rule.NativeTestRegistries.bootstrap();
        platformField = BaniraPlatforms.class.getDeclaredField("platform");
        platformField.setAccessible(true);
        previousPlatform = platformField.get(null);
        NeoForgeBaniraRegistryService registry = NeoForgeBaniraRegistryService.INSTANCE;
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(BaniraPlatform.class.getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registryService")) return registry;
                    throw new UnsupportedOperationException(method.toString());
                }));
    }
    @AfterClass public static void restorePlatform() throws Exception { platformField.set(null, previousPlatform); }

    @Test public void emptySafetyRulesDoNotComputeOffsetsOrReadWorld() {
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(new CommonConfig.BaseCategory(), null, false);
        CountingPosition pos = new CountingPosition();
        assertFalse(filter.isSafe(at(null, pos)));
        assertEquals("Empty safety rules must not allocate offset positions", 0, pos.belowCalls + pos.aboveCalls);
    }

    @Test public void belowRulesQueryExactPositionAndKeepTaskCache() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(settings, null, false);
        SafetyWorld world = new SafetyWorld();
        world.stoneAt = new BlockPos(4, 63, 8);
        CountingPosition pos = new CountingPosition();
        assertTrue(filter.isSafe(at(world, pos)));
        assertEquals(1, pos.belowCalls);
        assertEquals(0, pos.aboveCalls);
        assertEquals(Collections.singletonList(world.stoneAt), world.queries);
        assertTrue(filter.isSafe(at(world, pos)));
        assertEquals(1, world.queries.size());
        filter.clear();
        assertTrue(filter.isSafe(at(world, pos)));
        assertEquals(2, world.queries.size());
    }

    @Test public void aboveRulesSkipDisabledBelowAndRespectInsideShortCircuit() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocksAbove(Collections.singletonList("minecraft:stone"));
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(settings, null, false);
        SafetyWorld world = new SafetyWorld();
        world.stoneAt = new BlockPos(4, 65, 8);
        CountingPosition pos = new CountingPosition();
        assertTrue(filter.isSafe(at(world, pos)));
        assertEquals(0, pos.belowCalls);
        assertEquals(1, pos.aboveCalls);
        assertEquals(Collections.singletonList(world.stoneAt), world.queries);
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        world.stoneAt = pos;
        world.queries.clear();
        pos.belowCalls = pos.aboveCalls = 0;
        assertTrue(new NeoForgeSweepFilter(settings, null, false).isSafe(at(world, pos)));
        assertEquals(0, pos.belowCalls + pos.aboveCalls);
        assertEquals(Collections.singletonList(pos), world.queries);
    }

    private static final class CountingPosition extends BlockPos {
        int belowCalls, aboveCalls;
        CountingPosition() { super(4, 64, 8); }
        @Override public BlockPos below() { belowCalls++; return super.below(); }
        @Override public BlockPos above() { aboveCalls++; return super.above(); }
    }

    private static ItemEntity at(Level world, CountingPosition pos) {
        return new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, world) {
            @Override public BlockPos blockPosition() { return pos; }
        };
    }

    @Test public void safetyLookupObservesPositionChangeWithinSameWorld() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(settings, null, false);
        SafetyWorld world = new SafetyWorld();
        world.stoneAt = new BlockPos(12, 79, 8);
        ItemEntity entity = new ItemEntity(world, 4, 64, 8, net.minecraft.world.item.ItemStack.EMPTY);
        world.afterRead = () -> entity.setPos(12, 80, 8);
        assertTrue(filter.isSafe(entity));
        assertEquals(Arrays.asList(new BlockPos(4, 64, 8), world.stoneAt), world.queries);
    }

    @Test public void safetyLookupObservesWorldChangeWithoutPositionChange() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(4, 63, 8);
        ItemEntity entity = new ItemEntity(first, 4, 64, 8, net.minecraft.world.item.ItemStack.EMPTY);
        first.afterRead = () -> changeWorld(entity, second);
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    @Test public void eachEnabledBranchReadsCurrentEntityWorldAndPosition() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        NeoForgeSweepFilter filter = new NeoForgeSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(12, 79, 8);
        ItemEntity entity = new ItemEntity(first, 4, 64, 8, net.minecraft.world.item.ItemStack.EMPTY);
        first.afterRead = () -> { changeWorld(entity, second); entity.setPos(12, 80, 8); };
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    private static void changeWorld(Entity entity, Level world) {
        try {
            java.lang.reflect.Method method = Entity.class.getDeclaredMethod("setLevel", Level.class);
            method.setAccessible(true);
            method.invoke(entity, world);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static final class SafetyWorld extends Level {
        BlockPos stoneAt;
        final List<BlockPos> queries = new ArrayList<>();
        Runnable afterRead = () -> {};
        SafetyWorld() throws Exception {
            super(null, Level.OVERWORLD, xin.vanilla.aotake.rule.NativeTestRegistries.damageTypes(), registeredType(),
                    () -> net.minecraft.util.profiling.InactiveProfiler.INSTANCE, false, false, 0, 0);
        }
        private static net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> registeredType() throws Exception {
            net.minecraft.world.level.dimension.DimensionType type = overworldType();
            net.minecraft.core.MappedRegistry<net.minecraft.world.level.dimension.DimensionType> registry =
                    new net.minecraft.core.MappedRegistry<>(net.minecraft.core.registries.Registries.DIMENSION_TYPE, com.mojang.serialization.Lifecycle.stable());
            return registry.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD, type, net.minecraft.core.RegistrationInfo.BUILT_IN);
        }
        private static net.minecraft.world.level.dimension.DimensionType overworldType() throws Exception {
            return new net.minecraft.world.level.dimension.DimensionType(java.util.OptionalLong.empty(),
                    true, false, false, true, 1D, true, false, -64, 384, 384,
                    net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD,
                    net.minecraft.resources.ResourceLocation.tryParse("minecraft:overworld"), 0F,
                    new net.minecraft.world.level.dimension.DimensionType.MonsterSettings(false, true,
                            net.minecraft.util.valueproviders.UniformInt.of(0, 7), 0));
        }
        @Override public net.minecraft.world.level.chunk.ChunkSource getChunkSource() { throw new AssertionError("Unexpected chunk request"); }
        @Override public BlockState getBlockState(BlockPos pos) {
            queries.add(pos);
            BlockState state = (pos.equals(stoneAt) ? Blocks.STONE : Blocks.AIR).defaultBlockState();
            afterRead.run();
            return state;
        }
        @Override public List<? extends Player> players() { return Collections.emptyList(); }
        @Override public Collection<net.neoforged.neoforge.entity.PartEntity<?>> getPartEntities() { return Collections.emptyList(); }
        @Override public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) { throw new UnsupportedOperationException(); }
        @Override public void playSeededSound(Player p, double x, double y, double z, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> e, net.minecraft.sounds.SoundSource c, float v, float pitch, long seed) { throw new UnsupportedOperationException(); }
        @Override public void playSeededSound(Player p, Entity entity, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> e, net.minecraft.sounds.SoundSource c, float v, float pitch, long seed) { throw new UnsupportedOperationException(); }
        @Override public Entity getEntity(int id) { return null; }
        @Override public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId id) { throw new UnsupportedOperationException(); }
        @Override public void setMapData(net.minecraft.world.level.saveddata.maps.MapId id, net.minecraft.world.level.saveddata.maps.MapItemSavedData data) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.level.saveddata.maps.MapId getFreeMapId() { throw new UnsupportedOperationException(); }
        @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.scores.Scoreboard getScoreboard() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() { throw new UnsupportedOperationException(); }
        @Override public void levelEvent(Player player, int id, BlockPos pos, int data) { throw new UnsupportedOperationException(); }
        @Override public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.level.gameevent.GameEvent.Context context) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
        @Override public float getShade(net.minecraft.core.Direction direction, boolean shade) { throw new UnsupportedOperationException(); }
        @Override public int getBlockTint(BlockPos pos, net.minecraft.world.level.ColorResolver resolver) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.TickRateManager tickRateManager() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public void setDayTimeFraction(float value) { }
        @Override public float getDayTimeFraction() { return 0F; }
        @Override public float getDayTimePerTick() { return 1F; }
        @Override public void setDayTimePerTick(float value) { }
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
