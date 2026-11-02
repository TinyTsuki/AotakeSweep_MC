package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import xin.vanilla.aotake.enums.EnumProgressBarType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class SweepProgressUiParityTest {
    @Test
    public void textRotationRetainsOriginalFloatAndFullTurnBoundary() throws Exception {
        Method angle;
        try {
            angle = binding().getMethod("textAngleRadians", double.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Both HUD backends need the original text rotation boundary", missing);
        }
        assertEquals(0.0, (Double) angle.invoke(null, 0.0), 0);
        assertEquals(0.0, (Double) angle.invoke(null, 360.0), 0);
        assertEquals(0.0, (Double) angle.invoke(null, .00001), 0);
        assertEquals(Math.toRadians(90.0), (Double) angle.invoke(null, 90.0), 0);
        assertEquals(Math.toRadians((float) 45.1), (Double) angle.invoke(null, 45.1), 0);
    }

    @Test
    public void negativeAndRepeatedTurnsConvertOnlyOnce() throws Exception {
        Method angle = binding().getMethod("textAngleRadians", double.class);
        for (double value : new double[]{-90, -450, 720.5, 45.1, -45.1})
            assertEquals(Math.toRadians((float) value), (Double) angle.invoke(null, value), 0);
        for (double value : new double[]{-720, -360, 720, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(0, (Double) angle.invoke(null, value), 0);
    }

    private Class<?> binding() {
        try {
            return Class.forName("xin.vanilla.aotake.client.ui.SweepProgressUiBinding");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing shared sweep HUD binding", missing);
        }
    }

    private Object selection(List<EnumProgressBarType> normal, List<EnumProgressBarType> held,
                             boolean key, boolean gui, boolean leaf, boolean pole, boolean text) throws Exception {
        return binding().getMethod("selection", List.class, List.class, boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class).invoke(null, normal, held, key, gui, leaf, pole, text);
    }

    @Test
    public void everyCombinationKeepsItsOwnExperienceSwitch() throws Exception {
        lombok.var values = EnumProgressBarType.values();
        for (int mask = 0; mask < 8; mask++)
            for (int hides = 0; hides < 8; hides++) {
                List<EnumProgressBarType> modes = new ArrayList<>();
                for (int i = 0; i < 3; i++) if ((mask & 1 << i) != 0) modes.add(values[i]);
                boolean leaf = (hides & 1) != 0, pole = (hides & 2) != 0, text = (hides & 4) != 0;
                Object result = selection(modes, com.google.common.collect.ImmutableList.of(), false, false, leaf, pole, text);
                assertEquals(modes, result.getClass().getMethod("types").invoke(result));
                boolean hidden = modes.contains(EnumProgressBarType.LEAF) && leaf
                        || modes.contains(EnumProgressBarType.POLE) && pole || modes.contains(EnumProgressBarType.TEXT) && text;
                assertEquals(hidden, result.getClass().getMethod("hideExperience").invoke(result));
            }
    }

    @Test
    public void guiSuppressesHeldLayoutAndSelectedLayoutControlsPhase() throws Exception {
        Object held = selection(com.google.common.collect.ImmutableList.of(EnumProgressBarType.LEAF), com.google.common.collect.ImmutableList.of(EnumProgressBarType.TEXT), true, false, false, false, true);
        assertEquals(com.google.common.collect.ImmutableList.of(EnumProgressBarType.TEXT), held.getClass().getMethod("types").invoke(held));
        assertEquals(true, held.getClass().getMethod("hideExperience").invoke(held));
        Object gui = selection(com.google.common.collect.ImmutableList.of(EnumProgressBarType.LEAF), com.google.common.collect.ImmutableList.of(EnumProgressBarType.TEXT), true, true, false, false, true);
        assertEquals(com.google.common.collect.ImmutableList.of(EnumProgressBarType.LEAF), gui.getClass().getMethod("types").invoke(gui));
        assertEquals(false, gui.getClass().getMethod("hideExperience").invoke(gui));
        Method phase = binding().getMethod("drawInPhase", boolean.class, boolean.class);
        for (boolean hide : new boolean[]{false, true}) {
            assertNotEquals(phase.invoke(null, true, hide), phase.invoke(null, false, hide));
            assertEquals(hide, phase.invoke(null, true, hide));
        }
    }

    @Test
    public void sharedBindingIsNotAnotherMinimapRegistration() {
        assertTrue(xin.vanilla.banira.api.client.ui.UiHudBinding.class.isAssignableFrom(binding()));
    }

    @Test
    public void rendererAndMinimapKeepOneScheduleAndNoVendorImports() throws Exception {
        for (String path : new String[]{"client/ui/SweepProgressUiBinding", "screen/ProgressRender", "client/minimap/AotakeMinimapCountdown"}) {
            lombok.var node = new org.objectweb.asm.tree.ClassNode();
            try (java.io.InputStream in = getClass().getResourceAsStream("/xin/vanilla/aotake/" + path + ".class")) {
                assertNotNull(in);
                new org.objectweb.asm.ClassReader(in).accept(node, 0);
            }
            int schedules = 0, manualRenders = 0, angleBindings = 0;
            for (lombok.var m : node.methods)
                for (lombok.var i : m.instructions)
                    if (i instanceof org.objectweb.asm.tree.MethodInsnNode ) {
                        assertFalse(((org.objectweb.asm.tree.MethodInsnNode) i).owner, ((org.objectweb.asm.tree.MethodInsnNode) i).owner.startsWith("com/sighs/") || ((org.objectweb.asm.tree.MethodInsnNode) i).owner.startsWith("xin/vanilla/banira/internal/"));
                        assertFalse(((org.objectweb.asm.tree.MethodInsnNode) i).name, ((org.objectweb.asm.tree.MethodInsnNode) i).name.equals("getNextSweepTime") || ((org.objectweb.asm.tree.MethodInsnNode) i).name.equals("addMilliSecond"));
                        if (((org.objectweb.asm.tree.MethodInsnNode) i).owner.endsWith("BaniraMinimapLines") && ((org.objectweb.asm.tree.MethodInsnNode) i).name.equals("register")) schedules++;
                        if (((org.objectweb.asm.tree.MethodInsnNode) i).owner.endsWith("BaniraUi") && ((org.objectweb.asm.tree.MethodInsnNode) i).name.equals("renderHud")) manualRenders++;
                        if (((org.objectweb.asm.tree.MethodInsnNode) i).owner.endsWith("SweepProgressUiBinding") && ((org.objectweb.asm.tree.MethodInsnNode) i).name.equals("textAngleRadians"))
                            angleBindings++;
                    }
            assertEquals(path.endsWith("AotakeMinimapCountdown") ? 1 : 0, schedules);
            assertEquals(path.endsWith("ProgressRender") ? 1 : 0, manualRenders);
            assertEquals(path.endsWith("ProgressRender") ? 1 : 0, angleBindings);
        }
        String template;
        try (java.io.InputStream in = getClass().getResourceAsStream("/assets/apricityui/apricity/aotake_sweep/progress.html")) {
            assertNotNull(in);
            template = new String(com.google.common.io.ByteStreams.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(template.contains("aui-mouse-events\" content=\"none"));
        assertTrue(template.contains("pointer-events:none"));
        assertFalse(template.contains("<script"));
        assertTrue(template.length() < 1024);
        assertTrue(new SweepProgressUiBinding().spec().manual());
        assertFalse("The cleanup HUD must load its own overridable HTML template", new SweepProgressUiBinding().spec().shared());
    }

    @Test
    public void defaultHudStylesAreLightweightAndDoNotAddDecorativeChrome() throws Exception {
        String css;
        try (java.io.InputStream in = getClass().getResourceAsStream("/assets/apricityui/apricity/aotake_sweep/progress.css")) {
            assertNotNull("HTML styles must be resource-pack overridable", in);
            css = new String(com.google.common.io.ByteStreams.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(css.length() < 2048);
        assertTrue(css.contains("var(--hud-color)"));
        assertTrue(css.contains("var(--hud-angle)"));
        assertFalse(css.contains("box-shadow"));
        assertFalse(css.contains("border-radius"));
        assertFalse(css.contains("font-family"));
        assertFalse(css.contains("!important"));
        lombok.var node = new org.objectweb.asm.tree.ClassNode();
        try (java.io.InputStream in = getClass().getResourceAsStream("/xin/vanilla/aotake/screen/ProgressRender.class")) {
            new org.objectweb.asm.ClassReader(in).accept(node, 0);
        }
        lombok.var ids = new HashSet<String>();
        for (lombok.var m : node.methods)
            for (lombok.var insn : m.instructions)
                if (insn instanceof org.objectweb.asm.tree.LdcInsnNode  && ((org.objectweb.asm.tree.LdcInsnNode) insn).cst instanceof String  && ((String) ((org.objectweb.asm.tree.LdcInsnNode) insn).cst).startsWith("cleanup-"))
                    ids.add(((String) ((org.objectweb.asm.tree.LdcInsnNode) insn).cst));
        assertEquals(com.google.common.collect.ImmutableSet.of("cleanup-leaf", "cleanup-pole", "cleanup-time"), ids);
    }
}
