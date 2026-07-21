package xin.vanilla.aotake.bootstrap;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 约束客户端初始化和 NeoForge 专属事件只从显式 bootstrap/adapter 进入。 */
public class BootstrapStructureTest {

    @Test
    public void clientRegistrationIsExplicitAndIdempotent() throws Exception {
        String bootstrap = read("src/main/java/xin/vanilla/aotake/client/AotakeClientBootstrap.java");
        String handler = read("src/main/java/xin/vanilla/aotake/event/ClientModEventHandler.java");

        assertTrue(bootstrap.contains("private static boolean initialized"));
        assertTrue(bootstrap.contains("ClientModEventHandler.register();"));
        assertTrue(handler.contains("void register()"));
        assertFalse(handler.contains("static {"));
    }

    @Test
    public void neoForgeEventsStayInLoaderAdapter() throws Exception {
        String entry = read("src/main/java/xin/vanilla/aotake/AotakeSweep.java");
        String adapter = read("src/main/java/xin/vanilla/aotake/internal/neoforge/event/NeoForgeAotakeGameEventAdapter.java");

        assertTrue(entry.contains("NeoForgeAotakeGameEventAdapter.register(modEventBus);"));
        assertFalse(entry.contains("NeoForge.EVENT_BUS.addListener"));
        assertTrue(adapter.contains("ArrowNockEvent"));
        assertTrue(adapter.contains("ChunkVaultSession::onContainerClose"));
        assertTrue(adapter.contains("ModConfigEvent"));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        assertTrue("Missing bootstrap file: " + relativePath, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
