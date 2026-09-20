package xin.vanilla.aotake.config;

import org.junit.Test;
import org.junit.BeforeClass;
import xin.vanilla.aotake.config.access.ClientConfigAccess;
import xin.vanilla.aotake.config.access.CommonConfigAccess;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigViewBaselineTest {
    @BeforeClass
    public static void bootstrapRegistryDefaults() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
    }

    @Test
    public void commonPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        baseline.put("unbound", ConfigBaselineFixture.readView(CommonConfigAccess.root(null), CommonConfig.RootView.class));
        baseline.put("defaults", ConfigBaselineFixture.readView(CommonConfigAccess.root(fixture.holder), CommonConfig.RootView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(CommonConfigAccess.root(fixture.holder), CommonConfig.RootView.class));
        ConfigBaselineFixture.assertSnapshot("common", baseline);
    }

    @Test
    public void clientPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        baseline.put("unbound", ConfigBaselineFixture.readView(ClientConfigAccess.root(null), ClientConfig.RootView.class));
        baseline.put("defaults", ConfigBaselineFixture.readView(ClientConfigAccess.root(fixture.holder), ClientConfig.RootView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(ClientConfigAccess.root(fixture.holder), ClientConfig.RootView.class));
        ConfigBaselineFixture.assertSnapshot("client", baseline);
    }
}
