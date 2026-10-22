package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import java.lang.reflect.Method;
import java.util.*;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import static org.junit.Assert.*;

public class SweepProgressUiParityTest {
    @Test public void textRotationRetainsOriginalFloatAndFullTurnBoundary() throws Exception {
        Method angle;
        try { angle = binding().getMethod("textAngleRadians", double.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Both HUD backends need the original text rotation boundary", missing); }
        assertEquals(0.0, (Double) angle.invoke(null, 0.0), 0);
        assertEquals(0.0, (Double) angle.invoke(null, 360.0), 0);
        assertEquals(0.0, (Double) angle.invoke(null, .00001), 0);
        assertEquals(90.0, (Double) angle.invoke(null, 90.0), 0);
        assertEquals((double) (float) 45.1, (Double) angle.invoke(null, 45.1), 0);
    }
    private Class<?> binding() {
        try { return Class.forName("xin.vanilla.aotake.client.ui.SweepProgressUiBinding"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing shared sweep HUD binding", missing); }
    }
    private Object selection(List<EnumProgressBarType> normal, List<EnumProgressBarType> held,
                             boolean key, boolean gui, boolean leaf, boolean pole, boolean text) throws Exception {
        return binding().getMethod("selection", List.class, List.class, boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class).invoke(null, normal, held, key, gui, leaf, pole, text);
    }
    @Test public void everyCombinationKeepsItsOwnExperienceSwitch() throws Exception {
        var values = EnumProgressBarType.values();
        for (int mask = 0; mask < 8; mask++) for (int hides = 0; hides < 8; hides++) {
            List<EnumProgressBarType> modes = new ArrayList<>();
            for (int i = 0; i < 3; i++) if ((mask & 1 << i) != 0) modes.add(values[i]);
            boolean leaf = (hides & 1) != 0, pole = (hides & 2) != 0, text = (hides & 4) != 0;
            Object result = selection(modes, List.of(), false, false, leaf, pole, text);
            assertEquals(modes, result.getClass().getMethod("types").invoke(result));
            boolean hidden = modes.contains(EnumProgressBarType.LEAF) && leaf
                    || modes.contains(EnumProgressBarType.POLE) && pole || modes.contains(EnumProgressBarType.TEXT) && text;
            assertEquals(hidden, result.getClass().getMethod("hideExperience").invoke(result));
        }
    }
    @Test public void guiSuppressesHeldLayoutAndSelectedLayoutControlsPhase() throws Exception {
        Object held = selection(List.of(EnumProgressBarType.LEAF), List.of(EnumProgressBarType.TEXT), true, false, false, false, true);
        assertEquals(List.of(EnumProgressBarType.TEXT), held.getClass().getMethod("types").invoke(held));
        assertEquals(true, held.getClass().getMethod("hideExperience").invoke(held));
        Object gui = selection(List.of(EnumProgressBarType.LEAF), List.of(EnumProgressBarType.TEXT), true, true, false, false, true);
        assertEquals(List.of(EnumProgressBarType.LEAF), gui.getClass().getMethod("types").invoke(gui));
        assertEquals(false, gui.getClass().getMethod("hideExperience").invoke(gui));
        Method phase = binding().getMethod("drawInPhase", boolean.class, boolean.class);
        for (boolean hide : new boolean[]{false, true}) {
            assertNotEquals(phase.invoke(null, true, hide), phase.invoke(null, false, hide));
            assertEquals(hide, phase.invoke(null, true, hide));
        }
    }
    @Test public void sharedBindingIsNotAnotherMinimapRegistration() {
        assertTrue(xin.vanilla.banira.api.client.ui.UiHudBinding.class.isAssignableFrom(binding()));
    }
    @Test public void rendererAndMinimapKeepOneScheduleAndNoVendorImports() throws Exception {
        for (String path : new String[]{"client/ui/SweepProgressUiBinding", "screen/ProgressRender", "client/minimap/AotakeMinimapCountdown"}) {
            var node = new org.objectweb.asm.tree.ClassNode();
            try (var in = getClass().getResourceAsStream("/xin/vanilla/aotake/" + path + ".class")) {
                assertNotNull(in); new org.objectweb.asm.ClassReader(in).accept(node, 0);
            }
            int schedules = 0, manualRenders = 0, angleBindings = 0;
            for (var m : node.methods) for (var i : m.instructions) if (i instanceof org.objectweb.asm.tree.MethodInsnNode c) {
                assertFalse(c.owner, c.owner.startsWith("com/sighs/") || c.owner.startsWith("xin/vanilla/banira/internal/"));
                assertFalse(c.name, c.name.equals("getNextSweepTime") || c.name.equals("addMilliSecond"));
                if (c.owner.endsWith("BaniraMinimapLines") && c.name.equals("register")) schedules++;
                if (c.owner.endsWith("BaniraUi") && c.name.equals("renderHud")) manualRenders++;
                if (c.owner.endsWith("SweepProgressUiBinding") && c.name.equals("textAngleRadians")) angleBindings++;
            }
            assertEquals(path.endsWith("AotakeMinimapCountdown") ? 1 : 0, schedules);
            assertEquals(path.endsWith("ProgressRender") ? 1 : 0, manualRenders);
            assertEquals(path.endsWith("ProgressRender") ? 1 : 0, angleBindings);
        }
        String template;
        try (var in = getClass().getResourceAsStream("/assets/apricityui/apricity/aotake_sweep/progress.html")) {
            assertNotNull(in); template = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(template.contains("aui-mouse-events\" content=\"none"));
        assertTrue(template.contains("pointer-events:none")); assertFalse(template.contains("<script")); assertTrue(template.length() < 1024);
        assertTrue(new SweepProgressUiBinding().spec().manual());
        assertTrue(new SweepProgressUiBinding().spec().shared());
    }
}
