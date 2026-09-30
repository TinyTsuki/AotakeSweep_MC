package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import xin.vanilla.aotake.api.rule.*;
import java.util.*;
import java.util.function.Supplier;

public final class ForgeWorldAccess implements WorldAccess {
    private final Supplier<World> source;
    private final Runnable guard;
    private final Runnable entityGuard;
    public ForgeWorldAccess(Supplier<World> source, Runnable guard) {
        this(source, guard, guard);
    }
    public ForgeWorldAccess(Supplier<World> source, Runnable guard, Runnable entityGuard) {
        this.source = Objects.requireNonNull(source); this.guard = Objects.requireNonNull(guard);
        this.entityGuard = Objects.requireNonNull(entityGuard);
    }
    private World current() { guard.run(); return Objects.requireNonNull(source.get(), "world"); }
    @Override public String dimensionId() { return current().dimension().location().toString(); }
    @Override public long gameTime() { return current().getGameTime(); }
    @Override public List<EntityAccess> players() { return ForgeEntityAccess.views(current().players(), entityGuard); }
    @Override public List<EntityAccess> entities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] bounds = { minX, minY, minZ, maxX, maxY, maxZ };
        for (double value : bounds) if (!Double.isFinite(value)) throw new IllegalArgumentException("Entity bounds must be finite");
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Entity bounds are inverted");
        // Prevent overflow in vanilla's inclusive integer chunk loops.
        if (minX < -30000000D || minZ < -30000000D || maxX > 30000000D || maxZ > 30000000D) {
            throw new IllegalArgumentException("Entity bounds exceed horizontal world limits");
        }
        World world = current();
        return ForgeEntityAccess.views(world.getEntities((Entity) null,
                new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ), entity -> !entity.removed), entityGuard);
    }
    @Override public Optional<BlockAccess> block(int x, int y, int z) {
        World world = current();
        BlockPos pos = new BlockPos(x, y, z);
        if (!World.isInWorldBounds(pos)) return Optional.empty();
        Chunk chunk = world.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return Optional.empty();
        BlockState state = chunk.getBlockState(pos);
        return Optional.of(new BlockAccess() {
            @Override public String resourceId() { guard.run(); return Objects.requireNonNull(state.getBlock().getRegistryName()).toString(); }
            @Override public boolean air() { guard.run(); return state.isAir(); }
            @Override public <T> T nativeState(Class<T> type) { guard.run(); return ForgeEntityAccess.cast(state, type); }
        });
    }
    @Override public <T> T nativeWorld(Class<T> type) { return ForgeEntityAccess.cast(current(), type); }
}
