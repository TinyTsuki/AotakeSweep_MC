package xin.vanilla.aotake.util;

import xin.vanilla.aotake.enums.EnumListType;

/**
 * 统一 BLACK/WHITE 实体规则的清理语义。
 */
public final class EntityRulePolicy {
    private EntityRulePolicy() {
    }

    public static boolean shouldClean(boolean emptyRules, EnumListType mode, boolean matched) {
        if (emptyRules) {
            return mode == EnumListType.WHITE;
        }
        return mode == EnumListType.BLACK ? matched : !matched;
    }
}
