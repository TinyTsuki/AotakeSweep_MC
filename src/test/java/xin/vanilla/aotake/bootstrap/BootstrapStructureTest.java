package xin.vanilla.aotake.bootstrap;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 约束 Fabric 客户端只能从加载器 entrypoint 进入显式 bootstrap。 */
public class BootstrapStructureTest {

    @Test
    public void clientRegistrationIsExplicitAndIdempotent() throws Exception {
        String bootstrap = read("src/main/java/xin/vanilla/aotake/client/AotakeClientBootstrap.java");
        String handler = read("src/main/java/xin/vanilla/aotake/event/ClientModEventHandler.java");
        String entry = read("src/main/java/xin/vanilla/aotake/internal/fabric/client/FabricAotakeClientEntry.java");

        assertTrue(bootstrap.contains("private static boolean initialized"));
        assertTrue(bootstrap.contains("ClientModEventHandler.register();"));
        assertTrue(handler.contains("void register()"));
        assertFalse(handler.contains("static {"));
        assertTrue(entry.contains("AotakeClientBootstrap.init();"));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        assertTrue("Missing bootstrap file: " + relativePath, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
