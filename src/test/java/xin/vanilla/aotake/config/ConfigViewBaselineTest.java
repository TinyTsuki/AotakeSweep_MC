package xin.vanilla.aotake.config;

import org.junit.Test;
import org.junit.BeforeClass;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigViewBaselineTest {
    @BeforeClass
    public static void bootstrapRegistryDefaults() throws Exception {
        xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeNotificationsTest.bootstrapGame();
    }

    @Test
    public void commonPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(CommonConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        baseline.put("unbound", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        fixture.bind(CommonConfig.class);
        baseline.put("defaults", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(CommonConfigView.get(), CommonConfigView.class));
        ConfigBaselineFixture.assertSnapshot("common", baseline);
    }

    @Test
    public void clientPathsDefaultsAndReadsRemainEquivalent() throws Exception {
        ConfigBaselineFixture fixture = new ConfigBaselineFixture(ClientConfig.class);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("schema", fixture.schema());
        ConfigBaselineFixture.bind(ClientConfig.class, null);
        baseline.put("unbound", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        fixture.bind(ClientConfig.class);
        baseline.put("defaults", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        fixture.nonDefaultValues();
        baseline.put("changed", ConfigBaselineFixture.readView(ClientConfigView.get(), ClientConfigView.class));
        ConfigBaselineFixture.assertSnapshot("client", baseline);
    }
}
