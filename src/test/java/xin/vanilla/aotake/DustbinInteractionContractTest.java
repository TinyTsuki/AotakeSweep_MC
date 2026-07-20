package xin.vanilla.aotake;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 保证垃圾箱侧栏走真实点击、无斜杠命令和服务端翻页边界。 */
public class DustbinInteractionContractTest {
    @Test
    public void sidebarActionsUseLoaderNeutralCommandAndSmokePath() throws IOException {
        String open = read("src/main/java/xin/vanilla/aotake/network/packet/OpenDustbinToServer.java");
        String clear = read("src/main/java/xin/vanilla/aotake/network/packet/ClearDustbinToServer.java");
        String render = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");
        String smoke = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java");

        assertTrue(open.contains("DustbinPageNavigation.targetPage"));
        assertFalse(open.contains("String.format(\"/%s"));
        assertFalse(clear.contains("String.format(\"/%s"));
        assertTrue(render.contains("tooltip.toVanilla(AotakeLang.getClientLanguage())"));
        assertTrue(render.contains("aotake$addButton(button)"));
        assertTrue(smoke.contains("CLICK dustbin-refresh-sidebar"));
        assertTrue(smoke.contains("PASS dustbin-refresh-sidebar"));
        assertTrue(smoke.contains("Previous-page button is active on page one"));
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
