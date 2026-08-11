package xin.vanilla.aotake;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 保证 Banira 垃圾箱主题保持轻量面板、低对比槽位和独立视觉烟测。 */
public class DustbinThemeStyleContractTest {

    @Test
    public void themePaintUsesFineRoundedLowContrastSurfaces() throws IOException {
        String paint = read("src/main/java/xin/vanilla/aotake/screen/DustbinBaniraThemePaint.java");

        assertTrue(paint.contains("RoundedCornerMode.FINE"));
        assertTrue(paint.contains("drawFineRoundedRect"));
        assertFalse(paint.contains("fillOutLine"));
        assertTrue(readFloatConstant(paint, "SLOT_FILL_ALPHA") <= 0.35f);
        assertTrue(readFloatConstant(paint, "SLOT_BORDER_ALPHA") <= 0.30f);
    }

    @Test
    public void themedToolbarSeparatesDangerNormalAndPagingGroups() throws IOException {
        String render = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");

        assertTrue(render.contains("BANIRA_TOOLBAR_GROUP_GAP"));
        assertTrue(render.contains("toolbarGroupOffset"));
        assertEquals(2, countOccurrences(render,
                "toolbarGroupOffset += BANIRA_TOOLBAR_GROUP_GAP;"));
    }

    @Test
    public void smokeCapturesThemeAfterVanillaInteractionAndRestoresStyle() throws IOException {
        String smoke = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java");

        int vanillaOpen = smoke.indexOf("dustbinUiStyle(EnumDustbinClientUiStyle.VANILLA)");
        int vanillaInteraction = smoke.indexOf("PASS dustbin-refresh-sidebar");
        int themeOpen = smoke.indexOf("dustbinUiStyle(EnumDustbinClientUiStyle.BANIRA_THEME)");
        int themeCapture = smoke.indexOf("capture(client, \"08-dustbin-banira-theme\")");
        int restore = smoke.indexOf("restoreDustbinStyle()", themeCapture);

        assertTrue(vanillaOpen >= 0);
        assertTrue(vanillaInteraction > vanillaOpen);
        assertTrue(themeOpen > vanillaInteraction);
        assertTrue(themeCapture > themeOpen);
        assertTrue(restore > themeCapture);
        assertTrue(smoke.contains("capture(client, \"07-dustbin-refreshed\");\n" +
                "                if (phase == Phase.FINISHED)"));
        assertTrue(smoke.contains("capture(client, \"08-dustbin-banira-theme\");\n" +
                "                if (phase == Phase.FINISHED)"));
        assertTrue(smoke.contains("CLICK dustbin-refresh-sidebar"));
        assertTrue(smoke.contains("Previous-page button is active on page one"));
    }

    private static float readFloatConstant(String source, String name) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*([0-9.]+)f").matcher(source);
        assertTrue("Missing float constant " + name, matcher.find());
        return Float.parseFloat(matcher.group(1));
    }

    private static int countOccurrences(String source, String value) {
        int count = 0;
        int from = 0;
        while ((from = source.indexOf(value, from)) >= 0) {
            count++;
            from += value.length();
        }
        return count;
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
