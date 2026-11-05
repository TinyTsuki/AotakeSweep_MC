package xin.vanilla.aotake.internal.fabric;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 将 Fabric 回调消费规则集中在一处，避免候选操作误伤原版物品交互。
 */
public final class FabricInteractionPolicy {
    private FabricInteractionPolicy() {
    }

    public static Decision itemUse(boolean hasAotakeTag, boolean hasEntity, boolean hasPlayer) {
        return hasAotakeTag && (hasEntity || hasPlayer) ? Decision.CONSUME : Decision.PASS;
    }

    public static Decision duplicateEntityUse(boolean alreadyHandledThisTick) {
        return alreadyHandledThisTick ? Decision.CONSUME : Decision.PASS;
    }

    public record CaptureFallback(InteractionHand hand, ItemStack stack, long tick) {
        public boolean matches(InteractionHand currentHand, ItemStack currentStack, long currentTick) {
            // The interact and fallback packets can land on opposite sides of a tick boundary.
            return hand == currentHand && stack == currentStack
                    && (currentTick == tick || currentTick == tick + 1);
        }
    }

    public enum Decision {
        PASS,
        CONSUME
    }
}
