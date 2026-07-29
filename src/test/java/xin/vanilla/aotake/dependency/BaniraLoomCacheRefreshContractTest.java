package xin.vanilla.aotake.dependency;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BaniraLoomCacheRefreshContractTest {
    @Test
    public void publishedContentChangeInvalidatesCurrentLoomCache() throws Exception {
        String build = read("build.gradle");
        String refresh = read("gradle/banira-local-fingerprint.gradle");

        assertTrue(build.contains("apply from: \"gradle/banira-local-fingerprint.gradle\""));
        assertFalse(build.contains("refresh-banira-local.gradle"));
        assertTrue(refresh.contains("local-build.json"));
        assertTrue(refresh.contains("banira-local-state"));
        assertTrue(refresh.contains("remapped_mods"));
        assertTrue(refresh.contains("SHA-256"));
        assertTrue(refresh.contains("delete(staleRoot)"));
        assertFalse(refresh.contains("lastModified"));
        assertFalse(refresh.contains("worktree"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
