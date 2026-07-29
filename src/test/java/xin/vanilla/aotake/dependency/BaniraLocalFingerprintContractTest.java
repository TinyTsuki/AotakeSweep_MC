package xin.vanilla.aotake.dependency;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BaniraLocalFingerprintContractTest {
    @Test
    public void currentProjectRefreshesBaniraByPublishedContent() throws Exception {
        String buildScript = read("build.gradle");
        String fingerprintScript = read("gradle/banira-local-fingerprint.gradle");

        assertTrue(buildScript.contains("apply from: \"gradle/banira-local-fingerprint.gradle\""));
        assertTrue(fingerprintScript.contains("local-build.json"));
        assertTrue(fingerprintScript.contains("banira-local-state"));
        assertTrue(fingerprintScript.contains("remapped_mods"));
        assertTrue(fingerprintScript.contains("SHA-256"));
        assertTrue(fingerprintScript.contains("findByName(\"MavenLocal\")"));
        assertFalse(fingerprintScript.contains("System.getProperty(\"user.home\")"));
        assertFalse(fingerprintScript.contains("lastModified"));
        assertFalse(fingerprintScript.contains("worktree"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
