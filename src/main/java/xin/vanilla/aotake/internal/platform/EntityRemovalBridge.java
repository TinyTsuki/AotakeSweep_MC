package xin.vanilla.aotake.internal.platform;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;

/**
 * 隔离不同 Minecraft 版本的无掉落实体移除入口。
 */
public final class EntityRemovalBridge {
    private EntityRemovalBridge() {
    }

    public static void discard(Entity entity, boolean keepData) {
        // discard 直接移除实体，不进入 LivingEntity 死亡与战利品流程。
        entity.discard();
    }

    public static boolean isComplete(ServerLevel world, Entity entity) {
        return entity.isRemoved() && world.getEntity(entity.getId()) != entity
                && world.getEntity(entity.getUUID()) != entity;
    }
}
