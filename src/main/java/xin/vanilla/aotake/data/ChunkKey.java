package xin.vanilla.aotake.data;

import lombok.experimental.Accessors;
import net.minecraft.world.entity.Entity;

@Accessors(fluent = true)
public record ChunkKey(String dimension, int chunkX, int chunkZ, String entityType) {
    public ChunkKey(String dimension, int chunkX, int chunkZ) {
        this(dimension, chunkX, chunkZ, null);
    }

    public static ChunkKey of(Entity entity) {
        String dimension = entity.level != null
                ? entity.level.dimension().location().toString()
                : "unknown";
        int chunkX = entity.blockPosition().getX() >> 4;
        int chunkZ = entity.blockPosition().getZ() >> 4;
        return new ChunkKey(dimension, chunkX, chunkZ);
    }
}
