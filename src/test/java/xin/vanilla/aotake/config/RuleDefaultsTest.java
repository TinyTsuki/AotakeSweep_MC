package xin.vanilla.aotake.config;

import net.minecraft.server.Bootstrap;
import org.junit.Test;
import xin.vanilla.aotake.rule.BuiltinRules;

import static org.junit.Assert.*;

public class RuleDefaultsTest {
    @Test public void newConfigurationsUseManagedJavaRules() {
        Bootstrap.bootStrap();
        CommonConfig.BaseCategory config = new CommonConfig.BaseCategory();
        assertTrue(config.sweep().entityList().contains("script:" + BuiltinRules.CLEANUP));
        assertEquals(java.util.Collections.singletonList("script:" + BuiltinRules.PROTECTION),
                config.chunk().chunkCheckEntityList());
    }

    @Test public void sweepPolicyIsAnOrdinaryEditableConfigurationField() throws Exception {
        java.lang.reflect.Field field = CommonConfig.SweepSection.class.getDeclaredField("sweepPolicyId");
        field.setAccessible(true);
        assertEquals("builtin:default", field.get(new CommonConfig.SweepSection()));
    }
}
