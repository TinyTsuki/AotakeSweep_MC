package xin.vanilla.aotake;

import org.junit.Test;
import xin.vanilla.aotake.internal.client.dev.AotakeUiSmokeRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** NeoForge 分支必须显式接好无法由 Banira 泛型玩家事件覆盖的行为。 */
public class NeoForgeIntegrationContractTest {
    @Test
    public void arrowNockIsRegisteredOnNeoForgeBus() throws Exception {
        Path entry = Paths.get("src", "main", "java", "xin", "vanilla", "aotake", "AotakeSweep.java");
        String source = new String(Files.readAllBytes(entry), StandardCharsets.UTF_8);
        assertTrue("ArrowNockEvent must be registered directly on the NeoForge bus",
                source.contains("NeoForge.EVENT_BUS.addListener((ArrowNockEvent event)"));
    }

    @Test
    public void smokeRejectsProgressKeyPlayerListConflict() throws Exception {
        Method validator;
        try {
            validator = AotakeUiSmokeRunner.class.getDeclaredMethod("validateProgressKey", int.class, int.class);
        } catch (NoSuchMethodException e) {
            fail("Smoke runner must validate progress/player-list key conflicts");
            return;
        }
        validator.setAccessible(true);
        try {
            validator.invoke(null, 258, 258);
            fail("Matching progress and player-list keys must be rejected");
        } catch (InvocationTargetException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
    }
}
