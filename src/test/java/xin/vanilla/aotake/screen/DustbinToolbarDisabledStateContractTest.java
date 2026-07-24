package xin.vanilla.aotake.screen;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class DustbinToolbarDisabledStateContractTest {
    @Test
    public void disabledTexturedButtonHasVisibleDarkOverlay() throws Exception {
        String source = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");
        int texture = source.indexOf("AbstractGuiUtils.blitBlend(stack, texture");
        int restore = source.indexOf("RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)", texture);
        int darken = source.indexOf("AbstractGuiUtils.fill(stack, x, y, w, h, 0x66000000)", restore);

        assertTrue(texture >= 0 && restore > texture && darken > restore);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
