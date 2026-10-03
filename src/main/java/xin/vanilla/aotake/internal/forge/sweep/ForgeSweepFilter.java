package xin.vanilla.aotake.internal.forge.sweep;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.rule.RuleGeneration;
import xin.vanilla.aotake.sweep.DefaultSweepFilter;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.util.BlockUtils;
import xin.vanilla.banira.common.util.EntityUtils;

import java.util.*;

/**
 * Same captured scan data for read-only queries and task policy evaluation.
 */
public final class ForgeSweepFilter {
    private final CommonConfig.BaseCategory settings;
    private final RuleGeneration<Entity> generation;
    private final boolean chunk;
    private final SafeBlocks here, below, above;
    private final Map<World, Map<BlockPos, BlockState>> cache = new IdentityHashMap<>();

    public ForgeSweepFilter(CommonConfig.BaseCategory settings, RuleGeneration<Entity> generation, boolean chunk) {
        this.settings = settings;
        this.generation = generation;
        this.chunk = chunk;
        here = new SafeBlocks(settings.safe().safeBlocks());
        below = new SafeBlocks(settings.safe().safeBlocksBelow());
        above = new SafeBlocks(settings.safe().safeBlocksAbove());
    }

    public <E> DefaultSweepFilter.Entry<E, Object> inspect(E view, Entity entity) {
        if (!AotakeUtils.prepareSweepCandidate(entity)) return null;
        boolean safe = isSafe(entity);
        boolean junk = generation.shouldClean(chunk, entity);
        return new DefaultSweepFilter.Entry<>(view, safe, junk, junk ? null : EntityUtils.getEntityRegistryString(entity), safe ? ChunkKey.of(entity) : null);
    }

    public List<Entity> select(List<Entity> candidates) {
        return new DefaultSweepFilter<Entity, Object>(settings.sweep().entityListLimit(), settings.safe().safeBlocksEntityLimit(), e -> inspect(e, e)).select(candidates);
    }

    public void clear() {
        cache.clear();
    }

    boolean isSafe(Entity entity) {
        return here.matches(entity.level, entity.blockPosition())
                || below.enabled && below.matches(entity.level, entity.blockPosition().below())
                || above.enabled && above.matches(entity.level, entity.blockPosition().above());
    }

    private final class SafeBlocks {
        private final Set<String> names;
        private final Set<BlockState> states = new HashSet<>();
        private final boolean enabled;

        SafeBlocks(List<String> entries) {
            names = new HashSet<>(entries);
            for (String entry : entries) {
                BlockState state = BlockUtils.deserializeBlockState(entry);
                if (state != null) states.add(state);
            }
            enabled = !names.isEmpty() || !states.isEmpty();
        }

        boolean matches(World world, BlockPos pos) {
            if (!enabled) return false;
            BlockState state = cache.computeIfAbsent(world, ignored -> new HashMap<>()).computeIfAbsent(pos, world::getBlockState);
            return states.contains(state) || names.contains(BlockUtils.getBlockRegistryString(state));
        }
    }
}
