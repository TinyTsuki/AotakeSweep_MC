package xin.vanilla.aotake.internal.dev;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeGradleContractTest {
    @Test
    public void dedicatedSmokeKeepsClientAndServerStateExplicit() throws Exception {
        String script = new String(Files.readAllBytes(Paths.get("gradle", "network-smoke.gradle")), StandardCharsets.UTF_8);
        assertTrue(script.contains("aotake.networkSmoke.serverStatus"));
        assertTrue(script.contains("run-network-smoke"));
        assertTrue(script.contains("runServer"));
        assertTrue(script.contains("runClient"));
        assertTrue(script.contains("PASS burst-drop-cleanup"));
        assertTrue(script.contains("PASS global-batch-cleanup"));
    }
}
