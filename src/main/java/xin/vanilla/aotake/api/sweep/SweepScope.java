package xin.vanilla.aotake.api.sweep;

import java.util.Objects;

/**
 * Spatial upper bound; command-specific permissions remain enforced by the backend.
 */
public final class SweepScope {
    private final String dimension;
    private final int minX, minZ, maxX, maxZ;

    private SweepScope(String dimension, int minX, int minZ, int maxX, int maxZ) {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted chunk bounds");
        this.dimension = dimension;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public static SweepScope all() {
        return new SweepScope(null, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static SweepScope world(String dimension) {
        return chunks(dimension, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static SweepScope chunks(String dimension, int minX, int minZ, int maxX, int maxZ) {
        return new SweepScope(Objects.requireNonNull(dimension, "dimension"), minX, minZ, maxX, maxZ);
    }

    public boolean contains(String dimension, int chunkX, int chunkZ) {
        return (this.dimension == null || this.dimension.equals(dimension)) && chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
    }

    public boolean within(SweepScope upper) {
        return (upper.dimension == null || upper.dimension.equals(dimension)) && minX >= upper.minX && maxX <= upper.maxX && minZ >= upper.minZ && maxZ <= upper.maxZ;
    }

    public String dimension() {
        return dimension;
    }

    public int minChunkX() {
        return minX;
    }

    public int minChunkZ() {
        return minZ;
    }

    public int maxChunkX() {
        return maxX;
    }

    public int maxChunkZ() {
        return maxZ;
    }
}
