package xin.vanilla.aotake.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityRemovalContractTest {
    @Test
    public void removalUsesVersionBridgeWithoutKilledReason() throws Exception {
        String sweeper = read("src/main/java/xin/vanilla/aotake/util/EntitySweeper.java");
        String bridge = read("src/main/java/xin/vanilla/aotake/internal/platform/EntityRemovalBridge.java");

        assertTrue(sweeper.contains("EntityRemovalBridge.discard"));
        assertFalse(sweeper.contains("RemovalReason.KILLED"));
        assertTrue(bridge.contains("entity.remove()"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
