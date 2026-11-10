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
    @BeforeClass
    public static void bootstrap() {
        DustbinUiSessionTest.bootstrap();
    }

    @Test
    public void playerDraftSurvivesDetachAndOnlyOneSaveCallbackRuns() {
        lombok.var owner = new net.minecraft.client.gui.screen.Screen(new net.minecraft.util.text.StringTextComponent("")) {
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
        lombok.var owner = new net.minecraft.client.gui.screen.Screen(new net.minecraft.util.text.StringTextComponent("")) {
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
        lombok.var owner = new net.minecraft.client.gui.screen.Screen(new net.minecraft.util.text.StringTextComponent("")) {
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
                    if (instruction instanceof TypeInsnNode && ((TypeInsnNode) instruction).getOpcode() == org.objectweb.asm.Opcodes.NEW)
                        assertFalse(((TypeInsnNode) instruction).desc, ((TypeInsnNode) instruction).desc.contains("Menu") || ((TypeInsnNode) instruction).desc.endsWith("/Slot") || ((TypeInsnNode) instruction).desc.endsWith("/ChestScreen"));
                    if (instruction instanceof MethodInsnNode)
                        assertFalse(((MethodInsnNode) instruction).name, ((MethodInsnNode) instruction).name.equals("handleInventoryMouseClick") || ((MethodInsnNode) instruction).name.equals("slotClicked") || ((MethodInsnNode) instruction).name.equals("clicked"));
                    if (instruction instanceof FieldInsnNode && ((FieldInsnNode) instruction).getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD)
                        assertFalse(((FieldInsnNode) instruction).name, ((FieldInsnNode) instruction).name.equals("x") || ((FieldInsnNode) instruction).name.equals("y") || ((FieldInsnNode) instruction).name.equals("containerId"));
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
    public void forge16ContainerAndHudUseTheirActualEventContracts() throws Exception {
        lombok.var events = new java.util.HashSet<String>();
        for (lombok.var method : read("screen/DustbinRender").methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof TypeInsnNode) events.add(((TypeInsnNode) instruction).desc);
        for (String event : new String[]{"InitGuiEvent$Post", "MouseClickedEvent$Pre",
                "MouseReleasedEvent$Pre", "DrawScreenEvent$Post", "KeyboardKeyPressedEvent$Pre"})
            assertTrue(event, events.contains("net/minecraftforge/client/event/GuiScreenEvent$" + event));
        int stacks = 0;
        for (lombok.var method : read("screen/ProgressRender").methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.equals("net/minecraftforge/client/event/RenderGameOverlayEvent")) {
                    assertNotEquals("getPoseStack", ((MethodInsnNode) instruction).name);
                    if (((MethodInsnNode) instruction).name.equals("getMatrixStack")) stacks++;
                }
        assertEquals(2, stacks);
    }

    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream("xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
