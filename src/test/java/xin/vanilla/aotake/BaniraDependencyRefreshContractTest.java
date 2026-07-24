package xin.vanilla.aotake;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class BaniraDependencyRefreshContractTest {
    private static final Pattern ZERO_CHANGING_CACHE = Pattern.compile(
            "cacheChangingModulesFor\\s+0\\s*,\\s*['\"]seconds['\"]");

    @Test
    public void localBaniraDependenciesAlwaysRefreshDuringDevelopment() throws Exception {
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);

        assertTrue("Changing modules must not be cached", ZERO_CHANGING_CACHE.matcher(build).find());
        assertChanging(build, "implementation");
        assertChanging(build, "jarJar");
    }

    private static void assertChanging(String build, String configuration) {
        Pattern dependency = Pattern.compile(
                configuration + "\\([^\\r\\n]*baniraCodexCoords[^\\r\\n]*\\)\\s*\\{[^}]*changing\\s*=\\s*true",
                Pattern.DOTALL);
        assertTrue(configuration + " Banira dependency must be changing", dependency.matcher(build).find());
    }
}
