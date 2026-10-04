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
        // Native detachment clears scores and teams only after the entity is marked removed.
        entity.remove();
        world.despawn(entity);
    }
}
