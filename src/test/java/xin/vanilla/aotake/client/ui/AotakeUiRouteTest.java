package xin.vanilla.aotake.client.ui;

import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import xin.vanilla.aotake.test.NativeClientRule;
import xin.vanilla.banira.api.client.ui.UiCloseReason;
import xin.vanilla.banira.api.client.ui.UiDetachReason;
import xin.vanilla.banira.internal.client.ui.NativeUiView;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AotakeUiRouteTest {
    @Rule
    public final NativeClientRule client = new NativeClientRule();
    private BaniraPlatform previousPlatform;

    @Before
    public void headlessEnvironment() {
        previousPlatform = BaniraPlatforms.get();
        BaniraPlatforms.install((BaniraPlatform) java.lang.reflect.Proxy.newProxyInstance(
                BaniraPlatform.class.getClassLoader(), new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> method.getName().equals("isClient") ? false : method.invoke(previousPlatform, args)));
    }

    @After
    public void restoreEnvironment() {
        BaniraPlatforms.install(previousPlatform);
    }

    @BeforeClass
    public static void bootstrap() {
        DustbinUiSessionTest.bootstrap();
    }

    @Test
    public void playerDraftSurvivesDetachAndOnlyOneSaveCallbackRuns() {
        var owner = new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
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
        var owner = new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
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
        var owner = new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.empty()) {
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
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode type && type.getOpcode() == org.objectweb.asm.Opcodes.NEW)
                        assertFalse(type.desc, type.desc.contains("Menu") || type.desc.endsWith("/Slot") || type.desc.endsWith("/ContainerScreen"));
                    if (instruction instanceof MethodInsnNode call)
                        assertFalse(call.name, call.name.equals("handleInventoryMouseClick") || call.name.equals("slotClicked") || call.name.equals("clicked"));
                    if (instruction instanceof FieldInsnNode field && field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD)
                        assertFalse(field.name, field.name.equals("x") || field.name.equals("y") || field.name.equals("containerId"));
                }
        }
        assertEquals(3, xin.vanilla.aotake.enums.EnumDustbinClientUiStyle.values().length);
    }

    @Test
    public void nativeAndAuiCommandsUseOneSharedDispatcher() throws Exception {
        ClassNode node = read("screen/DustbinRender");
        int dispatchers = 0;
        for (MethodNode method : node.methods)
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("sendPacketToServer")) {
                    assertEquals("performUiAction", method.name);
                    dispatchers++;
                }
            }
        assertEquals(5, dispatchers);
    }

    @Test
    public void templatesAreLocalSmallAndContainerMouseTransparent() throws Exception {
        Path root = Path.of("src/main/resources/assets/apricityui/apricity/aotake_sweep");
        String html = Files.readString(root.resolve("dustbin.html"), StandardCharsets.UTF_8);
        String css = Files.readString(root.resolve("aotake.css"), StandardCharsets.UTF_8);
        assertTrue(html.contains("aui-mouse-events\" content=\"none"));
        assertTrue(css.contains("pointer-events:none"));
        long bytes = 0;
        for (String file : new String[]{"dustbin.html", "aotake.css"}) {
            String text = Files.readString(root.resolve(file), StandardCharsets.UTF_8);
            assertFalse(text.contains("<script"));
            assertFalse(text.contains("https:"));
            bytes += Files.size(root.resolve(file));
        }
        assertTrue(bytes < 4096);
    }

    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream("xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
