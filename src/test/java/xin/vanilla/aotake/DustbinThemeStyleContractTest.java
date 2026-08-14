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

/** 保证 Banira 垃圾箱主题保持单层柜体、轻量槽位和独立视觉烟测。 */
public class DustbinThemeStyleContractTest {

    @Test
    public void themePaintUsesSingleThemeMappedCabinetSurface() throws IOException {
        String paint = read("src/main/java/xin/vanilla/aotake/screen/DustbinBaniraThemePaint.java");

        assertTrue(paint.contains("RoundedCornerMode.FINE"));
        assertTrue(paint.contains("drawFineRoundedRect"));
        assertTrue(paint.contains("int cabinetFill = t.bgSurface()"));
        assertTrue(paint.contains("drawPlayerInventorySurface"));
        assertTrue(paint.contains("drawSlotSurfaceGrid"));
        assertFalse(paint.contains("fillOutLine"));
        assertFalse(paint.contains("LIGHT_NEUTRAL"));
        assertFalse(paint.contains("DARK_NEUTRAL"));
        assertFalse(paint.contains("neutralize("));
        assertFalse(paint.contains("drawRegionPanel"));
        assertTrue(readFloatConstant(paint, "SLOT_FILL_ALPHA") <= 0.35f);
        assertTrue(readFloatConstant(paint, "SLOT_BORDER_ALPHA") >= 0.35f);
        assertTrue(readFloatConstant(paint, "SLOT_BORDER_ALPHA") <= 0.50f);
        assertTrue(paint.contains("int slotBorder ="));
        assertTrue(paint.contains("drawSlotSurface(stack"));
    }

    @Test
    public void themedToolbarUsesCompactRailAndDirectThemeStates() throws IOException {
        String render = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");
        String toolbar = read("src/main/java/xin/vanilla/aotake/screen/DustbinBaniraToolbarButtonRenderer.java");

        assertTrue(render.contains("BANIRA_TOOLBAR_GROUP_GAP"));
        assertTrue(render.contains("BANIRA_TOOLBAR_CONTAINER_GAP = 2"));
        assertTrue(render.contains("baseX -= BANIRA_TOOLBAR_CONTAINER_GAP"));
        assertTrue(render.contains("toolbarGroupOffset"));
        assertEquals(2, countOccurrences(render,
                "toolbarGroupOffset += BANIRA_TOOLBAR_GROUP_GAP;"));
        assertTrue(render.contains("themedToolbarButtonCount"));
        assertTrue(render.contains("DustbinBaniraToolbarButtonRenderer.drawRail"));
        assertTrue(render.contains("baseW + 3, railHeight"));
        assertFalse(render.contains("baseW + 5, railHeight"));
        assertTrue(toolbar.contains("public static void drawRail"));
        assertTrue(toolbar.contains("theme.bgSecondary()"));
        assertTrue(toolbar.contains("theme.buttonBg()"));
        assertFalse(toolbar.contains("LIGHT_NEUTRAL"));
        assertFalse(toolbar.contains("DARK_NEUTRAL"));
        assertFalse(toolbar.contains("neutralize("));
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

    @Test
    public void themedReopenFailsClosedAfterEnteringTerminalPhase() throws IOException {
        String smoke = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java");
        int begin = smoke.indexOf("private void beginBaniraThemeDustbin");
        int end = smoke.indexOf("private void runDustbinThemeTick", begin);

        assertTrue("Missing themed dustbin reopen boundary", begin >= 0);
        assertTrue("Missing themed dustbin tick after reopen boundary", end > begin);
        String reopen = smoke.substring(begin, end);
        int enterThemePhase = reopen.indexOf("phase = Phase.DUSTBIN_THEME");
        int sendPacket = reopen.indexOf("PacketUtils.sendPacketToServer(new OpenDustbinToServer(0))");
        int catchFailure = reopen.indexOf("catch (RuntimeException error)");
        int failClosed = reopen.indexOf("fail(client, \"open-dustbin-banira-theme\", error)");

        assertTrue(enterThemePhase >= 0);
        assertTrue(sendPacket > enterThemePhase);
        assertTrue(catchFailure > sendPacket);
        assertTrue(failClosed > catchFailure);
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
