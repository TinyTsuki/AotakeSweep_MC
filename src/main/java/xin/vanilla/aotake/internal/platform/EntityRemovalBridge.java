package xin.vanilla.aotake.internal.platform;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

/**
 * 隔离不同 Minecraft 版本的无掉落实体移除入口。
 */
public final class EntityRemovalBridge {
    private EntityRemovalBridge() {
    }

    public static void discard(ServerLevel world, Entity entity, boolean keepData) {
        // Called at world START tick or shutdown, outside entity iteration.
        entity.discard();
    }

    public static boolean isComplete(ServerLevel world, Entity entity) {
        return entity.isRemoved() && world.getEntity(entity.getId()) != entity
                && world.getEntity(entity.getUUID()) != entity;
    }
}
