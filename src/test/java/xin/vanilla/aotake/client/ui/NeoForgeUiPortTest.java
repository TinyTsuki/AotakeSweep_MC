package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.Assert.*;

public class NeoForgeUiPortTest {
    private ClassNode read(String path) throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + path + ".class")) {
            assertNotNull(path, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void bothNeoForgeMousePhasesReachTheExistingScreenDispatcher() throws Exception {
        var found = new java.util.HashSet<String>();
        for (var method : read("event/ClientGameEventHandler").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/DustbinRender")
                        && call.name.equals("handleGuiScreen")) {
                    if (method.desc.contains("ScreenEvent$MouseButtonPressed$Pre")) found.add("press");
                    if (method.desc.contains("ScreenEvent$MouseButtonReleased$Pre")) found.add("release");
                }
        assertEquals(java.util.Set.of("press", "release"), found);
    }

    @Test
    public void containerIconsUseOnlyPublicMinecraft21ResourceFactories() throws Exception {
        int factories = 0;
        for (var method : read("client/ui/DustbinUiSession").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/resources/ResourceLocation")) {
                    assertNotEquals("<init>", call.name);
                    if (call.name.equals("fromNamespaceAndPath")) factories++;
                }
        assertEquals(3, factories);
    }

    @Test
    public void appearanceIsRegisteredAndReleasedByTheNativeLifecycle() throws Exception {
        int registrations = 0, releases = 0;
        for (var method : read("client/AotakeClientBootstrap").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/AotakeUiPages")
                        && call.name.equals("register")) registrations++;
        for (var method : read("mixin/ContainerScreenMixin").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/AotakeUiPages")
                        && call.name.equals("close")) releases++;
        assertEquals(1, registrations);
        assertEquals(1, releases);
    }
}
