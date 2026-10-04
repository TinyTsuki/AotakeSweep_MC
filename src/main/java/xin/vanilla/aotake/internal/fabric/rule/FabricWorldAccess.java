package xin.vanilla.aotake.internal.fabric.rule;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.Registry;
import xin.vanilla.aotake.api.rule.BlockAccess;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.rule.WorldAccess;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class FabricWorldAccess implements WorldAccess {
    private final Supplier<Level> source;
    private final Runnable guard;
    private final Runnable entityGuard;

    public FabricWorldAccess(Supplier<Level> source, Runnable guard) {
        this(source, guard, guard);
    }

    public FabricWorldAccess(Supplier<Level> source, Runnable guard, Runnable entityGuard) {
        this.source = Objects.requireNonNull(source);
        this.guard = Objects.requireNonNull(guard);
        this.entityGuard = Objects.requireNonNull(entityGuard);
    }

    private Level current() {
        guard.run();
        return Objects.requireNonNull(source.get(), "world");
    }

    @Override
    public String dimensionId() {
        return current().dimension().location().toString();
    }

    @Override
    public long gameTime() {
        return current().getGameTime();
    }

    @Override
    public List<EntityAccess> players() {
        return FabricEntityAccess.views(current().players(), entityGuard);
    }

    @Override
    public List<EntityAccess> entities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] bounds = {minX, minY, minZ, maxX, maxY, maxZ};
        for (double value : bounds)
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Entity bounds must be finite");
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Entity bounds are inverted");
        // Prevent overflow in vanilla's inclusive integer chunk loops.
        if (minX < -30000000D || minZ < -30000000D || maxX > 30000000D || maxZ > 30000000D) {
            throw new IllegalArgumentException("Entity bounds exceed horizontal world limits");
        }
        Level world = current();
        return FabricEntityAccess.views(world.getEntities((Entity) null,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ), entity -> !entity.removed), entityGuard);
    }

    @Override
    public Optional<BlockAccess> block(int x, int y, int z) {
        Level world = current();
        BlockPos pos = new BlockPos(x, y, z);
        if (!Level.isInWorldBounds(pos)) return Optional.empty();
        LevelChunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return Optional.empty();
        BlockState state = chunk.getBlockState(pos);
        return Optional.of(new BlockAccess() {
            @Override
            public String resourceId() {
                guard.run();
                return Objects.requireNonNull(Registry.BLOCK.getKey(state.getBlock())).toString();
            }

            @Override
            public boolean air() {
                guard.run();
                return state.isAir();
            }

            @Override
            public <T> T nativeState(Class<T> type) {
                guard.run();
                return FabricEntityAccess.cast(state, type);
            }
        });
    }

    @Override
    public <T> T nativeWorld(Class<T> type) {
        return FabricEntityAccess.cast(current(), type);
    }
}
