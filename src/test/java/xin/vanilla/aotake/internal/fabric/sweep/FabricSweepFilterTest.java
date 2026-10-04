package xin.vanilla.aotake.internal.fabric.sweep;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.tags.TagContainer;
import net.minecraft.core.Direction;
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
    @BeforeClass public static void bootstrap() throws Exception {
        Bootstrap.bootStrap();
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
    @AfterClass public static void restorePlatform() throws Exception { platformField.set(null, previousPlatform); }

    @Test public void emptySafetyRulesDoNotComputeOffsetsOrReadWorld() {
        FabricSweepFilter filter = new FabricSweepFilter(new CommonConfig.BaseCategory(), null, false);
        CountingPosition pos = new CountingPosition();
        assertFalse(filter.isSafe(at(null, pos)));
        assertEquals("Empty safety rules must not allocate offset positions", 0, pos.belowCalls + pos.aboveCalls);
    }

    @Test public void belowRulesQueryExactPositionAndKeepTaskCache() throws Exception {
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

    @Test public void aboveRulesSkipDisabledBelowAndRespectInsideShortCircuit() throws Exception {
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
        CountingPosition() { super(4, 64, 8); }
        @Override public BlockPos below() { belowCalls++; return super.below(); }
        @Override public BlockPos above() { aboveCalls++; return super.above(); }
    }

    private static ItemEntity at(Level world, CountingPosition pos) {
        return new ItemEntity(world, 4, 64, 8) {
            @Override public BlockPos blockPosition() { return pos; }
        };
    }

    @Test public void safetyLookupObservesPositionChangeWithinSameWorld() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld world = new SafetyWorld();
        world.stoneAt = new BlockPos(12, 79, 8);
        ItemEntity entity = new ItemEntity(world, 4, 64, 8);
        world.afterRead = () -> entity.setPos(12, 80, 8);
        assertTrue(filter.isSafe(entity));
        assertEquals(Arrays.asList(new BlockPos(4, 64, 8), world.stoneAt), world.queries);
    }

    @Test public void safetyLookupObservesWorldChangeWithoutPositionChange() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(4, 63, 8);
        ItemEntity entity = new ItemEntity(first, 4, 64, 8);
        first.afterRead = () -> entity.level = second;
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    @Test public void eachEnabledBranchReadsCurrentEntityWorldAndPosition() throws Exception {
        CommonConfig.BaseCategory settings = new CommonConfig.BaseCategory();
        settings.safe().safeBlocks(Collections.singletonList("minecraft:stone"));
        settings.safe().safeBlocksBelow(Collections.singletonList("minecraft:stone"));
        FabricSweepFilter filter = new FabricSweepFilter(settings, null, false);
        SafetyWorld first = new SafetyWorld(), second = new SafetyWorld();
        second.stoneAt = new BlockPos(12, 79, 8);
        ItemEntity entity = new ItemEntity(first, 4, 64, 8);
        first.afterRead = () -> { entity.level = second; entity.setPos(12, 80, 8); };
        assertTrue(filter.isSafe(entity));
        assertEquals(Collections.singletonList(new BlockPos(4, 64, 8)), first.queries);
        assertEquals(Collections.singletonList(second.stoneAt), second.queries);
    }

    private static final class SafetyWorld extends Level {
        BlockPos stoneAt;
        final List<BlockPos> queries = new ArrayList<>();
        Runnable afterRead = () -> {};
        SafetyWorld() throws Exception { super(null, Level.OVERWORLD, overworldType(), () -> InactiveProfiler.INSTANCE, false, false, 0); }
        private static DimensionType overworldType() throws Exception {
            Field field = DimensionType.class.getDeclaredField("DEFAULT_OVERWORLD");
            field.setAccessible(true);
            return (DimensionType) field.get(null);
        }
        @Override public BlockState getBlockState(BlockPos pos) {
            queries.add(pos);
            BlockState state = (pos.equals(stoneAt) ? Blocks.STONE : Blocks.AIR).defaultBlockState();
            afterRead.run();
            return state;
        }
        @Override public ChunkSource getChunkSource() { throw new AssertionError("Unexpected chunk request"); }
        @Override public List<? extends Player> players() { return Collections.emptyList(); }
        @Override public void sendBlockUpdated(BlockPos p, BlockState a, BlockState b, int flags) { throw new UnsupportedOperationException(); }
        @Override public void playSound(Player p, double x, double y, double z, SoundEvent e, SoundSource c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public void playSound(Player p, Entity entity, SoundEvent e, SoundSource c, float v, float pitch) { throw new UnsupportedOperationException(); }
        @Override public Entity getEntity(int id) { return null; }
        @Override public MapItemSavedData getMapData(String id) { throw new UnsupportedOperationException(); }
        @Override public void setMapData(MapItemSavedData data) { throw new UnsupportedOperationException(); }
        @Override public int getFreeMapId() { throw new UnsupportedOperationException(); }
        @Override public void destroyBlockProgress(int id, BlockPos pos, int progress) { throw new UnsupportedOperationException(); }
        @Override public Scoreboard getScoreboard() { throw new UnsupportedOperationException(); }
        @Override public RecipeManager getRecipeManager() { throw new UnsupportedOperationException(); }
        @Override public TagContainer getTagManager() { throw new UnsupportedOperationException(); }
        @Override public TickList<Block> getBlockTicks() { throw new UnsupportedOperationException(); }
        @Override public TickList<Fluid> getLiquidTicks() { throw new UnsupportedOperationException(); }
        @Override public void levelEvent(Player player, int id, BlockPos pos, int data) { throw new UnsupportedOperationException(); }
        @Override public Biome getUncachedNoiseBiome(int x, int y, int z) { throw new UnsupportedOperationException(); }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { throw new UnsupportedOperationException(); }
        @Override public float getShade(Direction direction, boolean shade) { throw new UnsupportedOperationException(); }
    }
}
