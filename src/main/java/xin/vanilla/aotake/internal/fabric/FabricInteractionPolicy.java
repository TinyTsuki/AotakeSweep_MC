package xin.vanilla.aotake.internal.fabric;

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

    public enum Decision {
        PASS,
        CONSUME
    }
}
