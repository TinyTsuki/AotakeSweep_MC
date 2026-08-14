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
        assertTrue(paint.contains("SLOT_FRAME_SIZE = 17"));
        assertTrue(paint.contains("drawFineRoundedRect(stack, x, y, SLOT_FRAME_SIZE, SLOT_FRAME_SIZE"));
        assertFalse(paint.contains("SLOT_RADIUS + 0.5f, border, 1"));
    }

    @Test
    public void themedToolbarUsesCompactRailAndDirectThemeStates() throws IOException {
        String render = read("src/main/java/xin/vanilla/aotake/screen/DustbinRender.java");
        String toolbar = read("src/main/java/xin/vanilla/aotake/screen/DustbinBaniraToolbarButtonRenderer.java");

        assertTrue(render.contains("BANIRA_TOOLBAR_GROUP_GAP"));
        assertTrue(render.contains("BANIRA_TOOLBAR_CONTAINER_GAP = 2"));
        assertTrue(render.contains("BANIRA_TOOLBAR_RAIL_PADDING = 2"));
        assertTrue(render.contains("baseX -= BANIRA_TOOLBAR_CONTAINER_GAP"));
        assertTrue(render.contains("baseY += BANIRA_TOOLBAR_RAIL_PADDING"));
        assertTrue(render.contains("toolbarGroupOffset"));
        assertEquals(2, countOccurrences(render,
                "toolbarGroupOffset += BANIRA_TOOLBAR_GROUP_GAP;"));
        assertTrue(render.contains("themedToolbarButtonCount"));
        assertTrue(render.contains("DustbinBaniraToolbarButtonRenderer.drawRail"));
        assertTrue(render.contains("int railX = baseX - baseW - 1 - BANIRA_TOOLBAR_RAIL_PADDING"));
        assertTrue(render.contains("int railY = baseY - BANIRA_TOOLBAR_RAIL_PADDING"));
        assertTrue(render.contains("int railWidth = baseW + BANIRA_TOOLBAR_RAIL_PADDING * 2"));
        assertFalse(render.contains("baseW + 5, railHeight"));
        assertTrue(toolbar.contains("public static void drawRail"));
        assertTrue(toolbar.contains("theme.bgSecondary()"));
        assertTrue(toolbar.contains("theme.buttonBg()"));
        assertFalse(toolbar.contains("LIGHT_NEUTRAL"));
        assertFalse(toolbar.contains("DARK_NEUTRAL"));
        assertFalse(toolbar.contains("neutralize("));
    }

    @Test
    public void smokeCapturesEveryDustbinStyleAndRestoresStyle() throws IOException {
        String smoke = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java");

        assertTrue(smoke.contains("EnumDustbinClientUiStyle.VANILLA"));
        assertTrue(smoke.contains("EnumDustbinClientUiStyle.TEXTURED"));
        assertTrue(smoke.contains("EnumDustbinClientUiStyle.BANIRA_THEME"));
        assertTrue(smoke.contains("DustbinGuiLayoutCache.invalidate()"));
        assertTrue(smoke.contains("PacketUtils.sendPacketToServer(new OpenDustbinToServer(0))"));
        assertTrue(smoke.contains("6 + dustbinStyleIndex"));
        assertTrue(smoke.contains("restoreDustbinStyle()"));
    }

    @Test
    public void dustbinStyleLoopFailsClosed() throws IOException {
        String smoke = read("src/main/java/xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner.java");
        int begin = smoke.indexOf("private void runDustbinTick");
        int end = smoke.indexOf("private void openDustbinStyle", begin);

        assertTrue("Missing dustbin style loop", begin >= 0);
        assertTrue("Missing dustbin reopen boundary", end > begin);
        String loop = smoke.substring(begin, end);
        assertTrue(loop.contains("assertDustbinStyle"));
        assertTrue(loop.contains("catch (RuntimeException e)"));
        assertTrue(loop.contains("fail(client, \"dustbin-"));
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
