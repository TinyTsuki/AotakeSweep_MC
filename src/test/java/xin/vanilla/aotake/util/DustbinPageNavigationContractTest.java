package xin.vanilla.aotake.util;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DustbinPageNavigationContractTest {
    @Test
    public void sharedPolicyRejectsPagesOutsideAvailableRange() throws Exception {
        Path policy = Paths.get("src/main/java/xin/vanilla/aotake/util/DustbinPageNavigation.java");
        assertTrue("Missing shared dustbin page policy", Files.isRegularFile(policy));

        Class<?> type = Class.forName("xin.vanilla.aotake.util.DustbinPageNavigation");
        Method targetPage = type.getMethod("targetPage", int.class, int.class, int.class);
        assertEquals(-1, targetPage.invoke(null, 1, 3, -1));
        assertEquals(-1, targetPage.invoke(null, 3, 3, 1));
        assertEquals(2, targetPage.invoke(null, 1, 3, 1));
    }

    @Test
    public void clientAndServerUseTheSharedBoundaryPolicy() throws Exception {
        String packet = read("src/main/java/xin/vanilla/aotake/network/packet/OpenDustbinToServer.java");
        int target = packet.indexOf("DustbinPageNavigation.targetPage(");
        int reject = packet.indexOf("if (targetPage < 1) return;");
        int close = packet.indexOf("player.closeContainer();");
        int execute = packet.indexOf("CommandUtils.executeCommand");

        assertTrue("Server packet must calculate the target through the shared policy", target >= 0);
        assertTrue("Invalid targets must be rejected before closing or executing", target < reject && reject < close && close < execute);

        String screen = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");
        assertFalse(screen.contains("private static boolean canNavigatePrev"));
        assertFalse(screen.contains("private static boolean canNavigateNext"));
        assertTrue(screen.contains("DustbinPageNavigation.canNavigate(current, total, -1)"));
        assertTrue(screen.contains("DustbinPageNavigation.canNavigate(current, total, 1)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
