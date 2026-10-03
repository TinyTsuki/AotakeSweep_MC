package xin.vanilla.aotake.internal.forge.sweep;

import net.minecraft.util.math.AxisAlignedBB;
import xin.vanilla.aotake.api.sweep.SweepScope;

/**
 * Original command authorization, independent of scope changes requested by a policy.
 */
public final class ForgeSweepTarget {
    private final String dimension;
    private final AxisAlignedBB bounds;
    private final boolean withEntity;

    public ForgeSweepTarget(String dimension, AxisAlignedBB bounds, boolean withEntity) {
        if (bounds != null && dimension == null) throw new IllegalArgumentException("Bounds require a dimension");
        this.dimension = dimension;
        this.bounds = bounds;
        this.withEntity = withEntity;
    }

    public static ForgeSweepTarget all() {
        return new ForgeSweepTarget(null, null, true);
    }

    AxisAlignedBB bounds() {
        return bounds;
    }

    public SweepScope scope() {
        return dimension == null ? SweepScope.all() : SweepScope.world(dimension);
    }

    public boolean contains(String dimension, AxisAlignedBB box, boolean player, boolean item) {
        return !player && (withEntity || item) && (this.dimension == null || this.dimension.equals(dimension))
                && (bounds == null || bounds.intersects(box));
    }
}
