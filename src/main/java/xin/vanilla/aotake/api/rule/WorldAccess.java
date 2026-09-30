package xin.vanilla.aotake.api.rule;

import java.util.List;
import java.util.Optional;

/** Owner-thread queries over loaded world state; returned lists are read-only snapshots. */
public interface WorldAccess {
    String dimensionId();
    long gameTime();
    List<EntityAccess> players();
    /** Finite ordered bounds within horizontal world limits; never loads missing chunks. */
    List<EntityAccess> entities(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    /** Empty for unloaded chunks or positions outside the world bounds. */
    Optional<BlockAccess> block(int x, int y, int z);
    <T> T nativeWorld(Class<T> type);
}
