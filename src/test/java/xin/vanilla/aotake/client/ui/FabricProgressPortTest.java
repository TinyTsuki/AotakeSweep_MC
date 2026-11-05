package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.Assert.*;

public class FabricProgressPortTest {
    private ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void fabricKeepsTheExistingHudCallbackAndGraphicsContract() throws Exception {
        int draws = 0;
        for (var method : read("event/ClientGameEventHandler").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/ProgressRender") && call.name.equals("render")) {
                    assertEquals("(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V", call.desc);
                    draws++;
                }
        assertEquals(1, draws);
        var renderer = read("screen/ProgressRender");
        assertTrue(renderer.methods.stream().anyMatch(method -> method.name.equals("render")
                && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Z)V")));
        for (var method : renderer.methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.startsWith("net/minecraftforge/"));
                    assertFalse(call.owner.startsWith("net/neoforged/"));
                }
    }

    @Test
    public void experienceIsHiddenOnlyWhenTheSynchronizedHudCanRender() throws Exception {
        var pre = read("event/ClientGameEventHandler").methods.stream()
                .filter(method -> method.name.equals("interceptExperience")).findFirst().orElseThrow();
        var calls = new java.util.ArrayList<String>();
        for (var instruction : pre.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/ProgressRender")) calls.add(call.name);
        assertEquals(java.util.List.of("shouldRenderProgressOverlay", "shouldHideVanillaExperienceBar"), calls);
        assertTrue(java.util.stream.StreamSupport.stream(pre.instructions.spliterator(), false)
                .anyMatch(instruction -> instruction.getOpcode() == org.objectweb.asm.Opcodes.IFEQ));
    }

    @Test
    public void disconnectClearsBothKeyAndFrameWhileReloadRebindsTheSameFrame() throws Exception {
        boolean keyReset = false, frameReset = false, keyTick = false;
        for (var method : read("event/ClientGameEventHandler").methods)
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                if (call.owner.endsWith("/SweepProgressUiBinding$KeyState") && call.name.equals("reset")) keyReset = true;
                if (call.owner.endsWith("/SweepProgressUiBinding$KeyState") && call.name.equals("tick")) keyTick = true;
                if (call.owner.endsWith("/ProgressRender") && call.name.equals("reset")) frameReset = true;
            }
        assertTrue(keyReset && frameReset && keyTick);
        var frame = new xin.vanilla.banira.api.client.ui.UiHudFrame(java.util.List.of(
                new xin.vanilla.banira.api.client.ui.UiHudFrame.Text("cleanup-time", "00:30", 0, 0, 30, 9, 9, 1, 0, 0, 0, -1)));
        var binding = new SweepProgressUiBinding();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var type = xin.vanilla.banira.api.client.ui.UiView.class;
        var view = (xin.vanilla.banira.api.client.ui.UiView) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (proxy, method, args) -> { if (method.getName().equals("hudFrame")) writes.incrementAndGet(); return null; });
        binding.prepare(frame);
        binding.bind(view);
        binding.detach(xin.vanilla.banira.api.client.ui.UiDetachReason.RELOAD);
        binding.bind(view);
        assertEquals(2, writes.get());
        binding.clear();
        assertFalse(binding.visible());
    }
}
