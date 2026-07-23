package xin.vanilla.aotake.util;

/**
 * 区块过载清理的数量策略。
 */
public final class ChunkCleanupPolicy {
    private ChunkCleanupPolicy() {
    }

    public static int retainedCount(int entityCount, int limit, double retainRatio) {
        if (entityCount <= 0 || limit <= 0) return 0;
        double ratio = Math.max(0.0, Math.min(1.0, retainRatio));
        int target = (int) Math.floor(limit * ratio);
        return Math.min(entityCount, target);
    }

    public static int removalCount(int entityCount, int limit, double retainRatio) {
        return Math.max(0, entityCount - retainedCount(entityCount, limit, retainRatio));
    }
}
