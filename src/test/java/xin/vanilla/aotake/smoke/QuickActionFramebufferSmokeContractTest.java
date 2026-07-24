package xin.vanilla.aotake.smoke;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class QuickActionFramebufferSmokeContractTest {
    @Test
    public void resourceIconIsVerifiedFromRenderedPixels() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java")),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("case QUICK_ACTION"));
        assertTrue(source.contains("countQuickActionIconPixels"));
        assertTrue(source.contains("Screenshot.takeScreenshot(client.getMainRenderTarget())"));
        assertTrue(source.contains("PASS quick-action-resource-icon pixels="));
        assertTrue(source.contains("image.getPixelRGBA"));
        assertTrue(source.contains("client.getWindow().getGuiScaledWidth()"));
        assertTrue(source.contains("for (int y = minY; y < maxY; y++)"));
        assertTrue(source.contains("for (int x = minX; x < maxX; x++)"));
    }
}
