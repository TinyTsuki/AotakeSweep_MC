package xin.vanilla.aotake.internal.platform;

import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.16.5 的无掉落实体移除入口；高版本只需替换这里的底层调用。
 */
public final class EntityRemovalBridge {
    private EntityRemovalBridge() {
    }

    public static void discard(ServerWorld world, Entity entity, boolean keepData) {
        // 直接从世界移除，不触发 LivingEntity 死亡和战利品流程。
        world.removeEntity(entity, keepData);
    }
}
