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
        int clientReady = script.indexOf("waitFor(client, clientStatus, 'PASS config-roundtrip', clientLog, 180)");
        int sparkWritten = script.indexOf("waitFor(server, serverStatus, 'PASS spark-report-written', serverLog, 120)");
        assertTrue(clientReady >= 0);
        assertTrue(sparkWritten > clientReady);
    }
}
