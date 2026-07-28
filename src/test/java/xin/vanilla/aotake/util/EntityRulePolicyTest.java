package xin.vanilla.aotake.util;

import org.junit.Test;
import xin.vanilla.aotake.enums.EnumListType;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityRulePolicyTest {

    @Test
    public void whiteModeProtectsMatchingEntities() {
        assertFalse(EntityRulePolicy.shouldClean(false, EnumListType.WHITE, true));
        assertTrue(EntityRulePolicy.shouldClean(false, EnumListType.WHITE, false));
    }

    @Test
    public void blackModeCleansOnlyMatchingEntities() {
        assertTrue(EntityRulePolicy.shouldClean(false, EnumListType.BLACK, true));
        assertFalse(EntityRulePolicy.shouldClean(false, EnumListType.BLACK, false));
    }

    @Test
    public void emptyRulesKeepExistingModeDefaults() {
        assertTrue(EntityRulePolicy.shouldClean(true, EnumListType.WHITE, false));
        assertFalse(EntityRulePolicy.shouldClean(true, EnumListType.BLACK, false));
    }
}
