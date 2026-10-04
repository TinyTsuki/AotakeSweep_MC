package xin.vanilla.aotake.internal.fabric.sweep;

import net.minecraft.world.phys.AABB;
import xin.vanilla.aotake.api.sweep.SweepScope;

/**
 * Original command authorization, independent of scope changes requested by a policy.
 */
public final class FabricSweepTarget {
    private final String dimension;
    private final AABB bounds;
    private final boolean withEntity;

    public FabricSweepTarget(String dimension, AABB bounds, boolean withEntity) {
        if (bounds != null && dimension == null) throw new IllegalArgumentException("Bounds require a dimension");
        this.dimension = dimension;
        this.bounds = bounds;
        this.withEntity = withEntity;
    }

    public static FabricSweepTarget all() {
        return new FabricSweepTarget(null, null, true);
    }

    AABB bounds() {
        return bounds;
    }

    public SweepScope scope() {
        return dimension == null ? SweepScope.all() : SweepScope.world(dimension);
    }

    public boolean contains(String dimension, AABB box, boolean player, boolean item) {
        return !player && (withEntity || item) && (this.dimension == null || this.dimension.equals(dimension))
                && (bounds == null || bounds.intersects(box));
    }
}
