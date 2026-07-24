package xin.vanilla.aotake.dependency;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class BaniraLoomCacheRefreshContractTest {
    @Test
    public void staleLocalBaniraRemapIsInvalidated() throws Exception {
        String build = read("build.gradle");
        String refresh = read("gradle/refresh-banira-local.gradle");

        assertTrue(build.contains("apply from: \"gradle/refresh-banira-local.gradle\""));
        assertTrue(refresh.contains("baniraMavenJar.lastModified()"));
        assertTrue(refresh.contains("remappedJar.lastModified() < baniraMavenJar.lastModified()"));
        assertTrue(refresh.contains("delete staleRoot"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
