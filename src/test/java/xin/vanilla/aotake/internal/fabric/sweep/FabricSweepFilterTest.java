package xin.vanilla.aotake.internal.fabric.sweep;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.Holder;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.rule.RuleTestItemEntity;
import xin.vanilla.banira.internal.fabric.platform.FabricBaniraRegistryService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.Assert.*;

public class FabricSweepFilterTest {
    private static Field platformField;
    private static Object previousPlatform;

    @BeforeClass
    public static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        xin.vanilla.aotake.test.NativeBootstrap.initialize();
        platformField = BaniraPlatforms.class.getDeclaredField("platform");
        platformField.setAccessible(true);
        previousPlatform = platformField.get(null);
        FabricBaniraRegistryService registry = FabricBaniraRegistryService.INSTANCE;
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(BaniraPlatform.class.getClassLoader(),
                new Class<?>[]{BaniraPlatform.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registryService")) return registry;
                    throw new UnsupportedOperationException(method.toString());
                }));
    }

    @AfterClass
    public static void restorePlatform() throws Exception {
        platformField.set(null, previousPlatform);
    }

    @Test
    public void emptySafetyRulesDoNotComputeOffsetsOrReadWorld() {
        FabricSweepFilter filter = new FabricSweepFilter(new CommonConfig.BaseCategory(), null, false);
        CountingPosition pos = new CountingPosition();
        assertFalse(filter.isSafe(at(null, pos)));
        assertEquals("Empty safety rules must not allocate offset positions", 0, pos.belowCalls + pos.aboveCalls);
    }

    @Test
    public void belowRulesQueryExactPositionAndKeepTaskCache() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
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

    @Test
    public void aboveRulesSkipDisabledBelowAndRespectInsideShortCircuit() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocksAbove(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
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
        assertTrue(new FabricSweepFilter(settings, null, false).isSafe(at(world, pos)));
        assertEquals(0, pos.belowCalls + pos.aboveCalls);
        assertEquals(Collections.singletonList(pos), world.queries);
    }

    private static final class CountingPosition extends BlockPos {
        int belowCalls, aboveCalls;

        CountingPosition() {
            super(4, 64, 8);
        }

        @Override
        public BlockPos below() {
            belowCalls++;
            return super.below();
        }

        @Override
        public BlockPos above() {
            aboveCalls++;
            return super.above();
        }
    }

    private static ItemEntity at(Level world, CountingPosition pos) {
        return new ItemEntity(net.minecraft.world.entity.EntityType.ITEM, world) {
            @Override
            public BlockPos blockPosition() {
                return pos;
            }
        };
    }

    @Test
    public void safetyLookupObservesPositionChangeWithinSameWorld() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld world = new SafetyWorld();
        world.stoneAt = new BlockPos(12, 79, 8);
        RuleTestItemEntity entity = new RuleTestItemEntity(world);
        entity.setPos(4, 64, 8);
        world.afterRead = () -> entity.setPos(12, 80, 8);
        assertTrue(filter.isSafe(entity));
        assertEquals(Arrays.asList(new BlockPos(4, 64, 8), world.stoneAt), world.queries);
    }

    @Test
    public void safetyLookupObservesWorldChangeWithoutPositionChange() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(4, 63, 8);
        RuleTestItemEntity entity = new RuleTestItemEntity(first);
        entity.setPos(4, 64, 8);
        first.afterRead = () -> entity.moveToWorld(second);
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    @Test
    public void eachEnabledBranchReadsCurrentEntityWorldAndPosition() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(12, 79, 8);
        RuleTestItemEntity entity = new RuleTestItemEntity(first);
        entity.setPos(4, 64, 8);
        first.afterRead = () -> {
            entity.moveToWorld(second);
            entity.setPos(12, 80, 8);
        };
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    private static final class SafetyWorld extends Level {
        BlockPos stoneAt;
        final List<BlockPos> queries = new ArrayList<>();
        Runnable afterRead = () -> {
        };

        SafetyWorld() throws Exception {
            super(null, Level.OVERWORLD, xin.vanilla.aotake.rule.RuleTestRegistries.access(), overworldHolder(), false, false, 0, 0);
        }

        private static Holder<DimensionType> overworldHolder() {
            return xin.vanilla.aotake.rule.RuleTestRegistries.access()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                    .getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            queries.add(pos);
            BlockState state = (pos.equals(stoneAt) ? Blocks.STONE : Blocks.AIR).defaultBlockState();
            afterRead.run();
            return state;
        }

        @Override
        public ChunkSource getChunkSource() {
            throw new AssertionError("Unexpected chunk request");
        }

        @Override
        public List<? extends Player> players() {
            return Collections.emptyList();
        }

        @Override
        public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void playSeededSound(Entity p, double x, double y, double z, Holder<SoundEvent> e, SoundSource c, float v, float pitch, long seed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void playSeededSound(Entity p, Entity entity, Holder<SoundEvent> e, SoundSource c, float v, float pitch, long seed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entity getEntity(int id) {
            return null;
        }

        @Override
        public MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void destroyBlockProgress(int id, BlockPos pos, int progress) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.TickRateManager tickRateManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scoreboard getScoreboard() {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeAccess recipeAccess() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LevelTickAccess<Block> getBlockTicks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LevelTickAccess<Fluid> getFluidTicks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void levelEvent(Entity player, int id, BlockPos pos, int data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.core.RegistryAccess registryAccess() {
            return xin.vanilla.aotake.rule.RuleTestRegistries.access();
        }

        @Override
        protected LevelEntityGetter<Entity> getEntities() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void gameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event, net.minecraft.world.phys.Vec3 pos, net.minecraft.world.level.gameevent.GameEvent.Context context) {
        }

        @Override
        public net.minecraft.core.Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
            return net.minecraft.world.flag.FeatureFlags.VANILLA_SET;
        }

        @Override
        public String gatherChunkSourceStats() {
            return "safety-test";
        }

        @Override
        public net.minecraft.world.level.block.entity.FuelValues fuelValues() {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.clock.ClockManager clockManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Collection<net.minecraft.world.entity.boss.enderdragon.EnderDragonPart> dragonParts() {
            return List.of();
        }

        @Override
        public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void explode(Entity entity, net.minecraft.world.damagesource.DamageSource damage,
                            net.minecraft.world.level.ExplosionDamageCalculator calculator,
                            double x, double y, double z, float radius, boolean fire, ExplosionInteraction interaction,
                            net.minecraft.core.particles.ParticleOptions small, net.minecraft.core.particles.ParticleOptions large,
                            net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> particles,
                            Holder<SoundEvent> sound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextSubTickCount() {
            return 0;
        }

        @Override
        public int getSeaLevel() {
            return 63;
        }

        @Override
        public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
            throw new UnsupportedOperationException();
        }
    }
}
