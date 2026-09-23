package xin.vanilla.aotake.config;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GeneratedConfigSchemaTest {
    @BeforeClass public static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test public void commonSchemaMatchesRegistration() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        assertEquals(fixture.defaults, ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
    }

    @Test public void clientSchemaMatchesRegistration() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        fixture.bind(ClientConfig.class);
        assertEquals(fixture.defaults, ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
    }

    @Test public void ruleListsAreIndependentAndKeepCommas() throws Exception {
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        List<String> original = new ArrayList<>(CommonConfigView.get().base().sweep().entityList());
        CommonConfigView.get().base().sweep().entityList().clear();
        assertEquals(original, CommonConfigView.get().base().sweep().entityList());
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        fixture.bind(CommonConfig.class);
        List<String> rules = Arrays.asList("tick, clazz, itemClazz -> tick >= 5", "minecraft:arrow");
        CommonConfigView.get().base().sweep().entityList(rules);
        assertEquals(rules, CommonConfigView.get().base().sweep().entityList());
        CommonConfigView.get().base().sweep().entityList().clear();
        assertEquals(rules, fixture.holder.get("base.sweep.entityList"));
        assertEquals(0, fixture.saves);
        CommonConfigView.get().handle().save();
        assertEquals(1, fixture.saves);
    }
}
