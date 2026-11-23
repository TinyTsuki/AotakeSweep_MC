package xin.vanilla.banira.generated;

import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.rule.EntityRuleContext;

public final class CreateProcessingProtection implements EntityRule {
    private static final String[] PROCESSING_TIME = {"CreateData", "Processing", "Time"};

    @Override
    public boolean test(EntityRuleContext context) {
        if (!context.isItem()) return false;
        Integer time = context.persistentInt(PROCESSING_TIME);
        return time != null && time.intValue() > 0;
    }
}
