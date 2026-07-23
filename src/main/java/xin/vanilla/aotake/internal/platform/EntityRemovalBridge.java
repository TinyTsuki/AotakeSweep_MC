package xin.vanilla.aotake.internal.platform;

import net.minecraft.world.entity.Entity;

/**
 * 隔离不同 Minecraft 版本的无掉落实体移除入口。
 */
public final class EntityRemovalBridge {
    private EntityRemovalBridge() {
    }

    public static void discard(Entity entity, boolean keepData) {
        // 1.16.5 的 remove 直接移除实体，不进入 LivingEntity 死亡与战利品流程。
        entity.remove();
    }
}
