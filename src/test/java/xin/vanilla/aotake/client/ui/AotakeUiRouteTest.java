package xin.vanilla.aotake.client.ui;

import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.internal.client.ui.NativeUiView;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AotakeUiRouteTest {
    private static xin.vanilla.banira.platform.BaniraPlatform originalPlatform;

    @BeforeClass
    public static void bootstrap() {
        DustbinUiSessionTest.bootstrap();
        originalPlatform = xin.vanilla.banira.platform.BaniraPlatforms.get();
        lombok.var type = xin.vanilla.banira.platform.BaniraPlatform.class;
        xin.vanilla.banira.platform.BaniraPlatforms.install(
                (xin.vanilla.banira.platform.BaniraPlatform) java.lang.reflect.Proxy.newProxyInstance(
                        type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                            if (method.getName().equals("isClient")) return false;
                            try {
                                return method.invoke(originalPlatform, args);
                            } catch (java.lang.reflect.InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        }));
    }

    @org.junit.AfterClass
    public static void restorePlatform() {
        if (originalPlatform != null) xin.vanilla.banira.platform.BaniraPlatforms.install(originalPlatform);
    }

    @Test
    public void playerDraftSurvivesDetachAndOnlyOneSaveCallbackRuns() {
        lombok.var owner = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        AtomicInteger saves = new AtomicInteger();
        AotakePlayerUiSession session = new AotakePlayerUiSession(owner, true, false, (show, voice) -> {
            assertFalse(show);
            assertTrue(voice);
            saves.incrementAndGet();
        });
        NativeUiView old = new NativeUiView(owner);
        session.shared().bind(old);
        session.showResult(false);
        session.warningVoice(true);
        session.shared().detach(UiDetachReason.SWITCH);
        NativeUiView next = new NativeUiView(owner);
        session.shared().bind(next);
        old.activate("submit");
        assertEquals(0, saves.get());
        assertFalse(session.showResult());
        assertTrue(session.warningVoice());
        next.activate("submit");
        next.activate("submit");
        session.submit();
        assertEquals(1, saves.get());
        session.shared().close(UiCloseReason.USER_CLOSE);
        assertEquals(0, next.subscriptionCount());
    }

    @Test
    public void rejectedSavePreservesDraftAndCanBeRetried() {
        lombok.var owner = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        AtomicInteger attempts = new AtomicInteger();
        AotakePlayerUiSession session = new AotakePlayerUiSession(owner, false, true, (show, voice) -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Disconnected");
        });
        assertFalse(session.submit());
        assertFalse(session.showResult());
        assertTrue(session.warningVoice());
        assertTrue(session.submit());
        assertFalse(session.submit());
        assertEquals(2, attempts.get());
    }

    @Test
    public void invalidBooleanDraftCannotBeSilentlySavedAsFalse() {
        lombok.var owner = new net.minecraft.client.gui.screens.Screen(new net.minecraft.network.chat.TextComponent("")) {
        };
        for (String field : new String[]{"showSweepResult", "enableWarningVoice"}) {
            AtomicInteger saves = new AtomicInteger();
            AotakePlayerUiSession session = new AotakePlayerUiSession(owner, true, true, (show, voice) -> saves.incrementAndGet());
            session.shared().fields().draft(field, "invalid");
            assertFalse("Malformed preference accepted: " + field, session.submit());
            assertEquals(0, saves.get());
            assertEquals("invalid", session.shared().fields().draft(field));
            session.shared().fields().draft(field, "false");
            assertTrue(session.submit());
            assertEquals(1, saves.get());
            assertFalse(session.submit());
        }
    }

    @Test
    public void containerAppearanceNeverConstructsMenusOrForwardsInventoryClicks() throws Exception {
        for (String name : new String[]{"AotakeUiPages", "DustbinUiSession"}) {
            ClassNode node = read("client/ui/" + name);
            for (MethodNode method : node.methods)
                for (lombok.var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode && instruction.getOpcode() == org.objectweb.asm.Opcodes.NEW) {
                        String type = ((TypeInsnNode) instruction).desc;
                        assertFalse(type, type.contains("Menu") || type.endsWith("/Slot") || type.endsWith("/ContainerScreen"));
                    }
                    if (instruction instanceof MethodInsnNode) {
                        String operation = ((MethodInsnNode) instruction).name;
                        assertFalse(operation, operation.equals("handleInventoryMouseClick") || operation.equals("slotClicked") || operation.equals("clicked"));
                    }
                    if (instruction instanceof FieldInsnNode && instruction.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD) {
                        String fieldName = ((FieldInsnNode) instruction).name;
                        assertFalse(fieldName, fieldName.equals("x") || fieldName.equals("y") || fieldName.equals("containerId"));
                    }
                }
        }
        assertEquals(3, xin.vanilla.aotake.enums.EnumDustbinClientUiStyle.values().length);
    }

    @Test
    public void nativeAndAuiCommandsUseOneSharedDispatcher() throws Exception {
        ClassNode node = read("screen/DustbinRender");
        int dispatchers = 0;
        for (MethodNode method : node.methods)
            for (lombok.var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("sendPacketToServer")) {
                    assertEquals("performUiAction", method.name);
                    dispatchers++;
                }
            }
        assertEquals(5, dispatchers);
    }

    @Test
    public void templatesAreLocalSmallAndContainerMouseTransparent() throws Exception {
        Path root = java.nio.file.Paths.get("src/main/resources/assets/apricityui/apricity/aotake_sweep");
        String html = UiBoundaryChecks.text(root.resolve("dustbin.html"), StandardCharsets.UTF_8);
        String css = UiBoundaryChecks.text(root.resolve("aotake.css"), StandardCharsets.UTF_8);
        assertTrue(html.contains("aui-mouse-events\" content=\"none"));
        assertTrue(css.contains("pointer-events:none"));
        long bytes = 0;
        for (String file : new String[]{"dustbin.html", "aotake.css"}) {
            String text = UiBoundaryChecks.text(root.resolve(file), StandardCharsets.UTF_8);
            assertFalse(text.contains("<script"));
            assertFalse(text.contains("https:"));
            bytes += Files.size(root.resolve(file));
        }
        assertTrue(bytes < 4096);
    }

    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (lombok.var stream = getClass().getClassLoader().getResourceAsStream("xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
