package xin.vanilla.aotake.data;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.entity.Entity;

import java.util.Objects;

@Getter
@Accessors(fluent = true)
public class ChunkKey {
    private final String dimension;
    private final int chunkX;
    private final int chunkZ;
    private final String entityType;

    public ChunkKey(String dimension, int chunkX, int chunkZ) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityType = null;
    }

    public ChunkKey(String dimension, int chunkX, int chunkZ, String entityType) {
        this.dimension = dimension;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityType = entityType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChunkKey chunkKey = (ChunkKey) obj;
        return chunkX == chunkKey.chunkX
                && chunkZ == chunkKey.chunkZ
                && Objects.equals(dimension, chunkKey.dimension)
                && Objects.equals(entityType, chunkKey.entityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, chunkX, chunkZ, entityType);
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
