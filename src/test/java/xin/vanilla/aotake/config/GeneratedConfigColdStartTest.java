package xin.vanilla.aotake.config;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GeneratedConfigColdStartTest {
    // Run this class alone in a fresh test JVM before any registry-bootstrap fixtures.
    @Test public void commandDefaultsDoNotInitializeRegistryDependentCategories() {
        ConfigBaselineFixture.bind(CommonConfig.class, null);
        assertEquals("aotake", CommonConfigView.get().command().commandPrefix());
        assertEquals("dustbin", CommonConfigView.get().command().commandDustbinOpen());
    }
}
