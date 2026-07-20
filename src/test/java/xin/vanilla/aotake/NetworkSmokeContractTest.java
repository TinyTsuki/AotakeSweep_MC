package xin.vanilla.aotake;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** 保证 dedicated server/client smoke 的入口、阶段和清理逻辑保持完整。 */
public class NetworkSmokeContractTest {
    @Test
    public void dedicatedNetworkSmokeIsFullyWired() throws IOException {
        String build = read("build.gradle");
        String script = read("gradle/network-smoke.gradle");
        String server = read("src/main/java/xin/vanilla/aotake/internal/server/dev/AotakeNetworkSmokeServerRunner.java");
        String client = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeNetworkSmokeClientRunner.java");
        String main = read("src/main/java/xin/vanilla/aotake/AotakeSweep.java");
        String configCommand = read("src/main/java/xin/vanilla/aotake/command/impl/ConfigCommand.java");
        String clientBootstrap = read("src/main/java/xin/vanilla/aotake/client/AotakeClientBootstrap.java");
        String clientEvents = read("src/main/java/xin/vanilla/aotake/event/ClientGameEventHandler.java");
        String ignore = read(".gitignore");

        assertContains(build, "apply from: 'gradle/network-smoke.gradle'");
        assertContains(script, "networkSmoke");
        assertContains(script, "runNetworkSmokeServer");
        assertContains(script, "runNetworkSmokeClient");
        assertContains(script, "phase-one");
        assertContains(script, "phase-two");
        assertContains(script, "destroyProcessTree");
        assertContains(script, "FINISHED");
        assertContains(server, "PASS persisted-player-data");
        assertContains(server, "PASS persisted-world-data");
        assertContains(server, "PASS config-command-roundtrip");
        assertContains(server, "PASS persisted-command-config");
        assertContains(server, "aotake config common base.batch.sweepBatchLimit");
        assertContains(server, "server.halt(false)");
        assertContains(client, "PlayerConfigSyncToServer");
        assertContains(client, "ConnectingScreen");
        assertContains(client, "OpenDustbinToServer");
        assertContains(main, "AotakeNetworkSmokeServerRunner");
        assertTrue("Config registration must precede common setup listener registration",
                main.indexOf("BaniraConfig.register(CommonConfig.class, MODID)")
                        < main.indexOf("addListener(this::onCommonSetup)"));
        assertContains(clientBootstrap, "AotakeNetworkSmokeClientRunner.register()");
        assertContains(clientEvents, "AotakeNetworkSmokeClientRunner.tick");
        assertContains(configCommand, "Commands.literal(\"common\")");
        assertNotContains(configCommand, "Commands.literal(\"server\")");
        assertContains(ignore, "/run-network-smoke/");
    }

    private static String read(String relativePath) throws IOException {
        Path path = Paths.get(relativePath);
        assertTrue("Missing network smoke file: " + relativePath, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertContains(String source, String expected) {
        assertTrue("Missing network smoke contract fragment: " + expected, source.contains(expected));
    }

    private static void assertNotContains(String source, String unexpected) {
        assertTrue("Unexpected duplicate config command fragment: " + unexpected, !source.contains(unexpected));
    }
}
