package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.Assert.*;

public class FabricUiPortTest {
    private ClassNode read(String path) throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + path + ".class")) {
            assertNotNull(path, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    @Test
    public void bothFabricMousePhasesReachTheSessionOnceWithoutReplayingNativeInput() throws Exception {
        var found = new java.util.HashMap<String, Integer>();
        for (var method : read("internal/fabric/client/FabricAotakeClientEntry").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.owner.startsWith("net/neoforged/"));
                    assertFalse(call.name.equals("mouseClicked") || call.name.equals("mouseReleased"));
                    if (call.owner.endsWith("/AotakeUiPages") && java.util.Set.of("click", "release").contains(call.name))
                        found.merge(call.name, 1, Integer::sum);
                    if (call.owner.endsWith("/ScreenMouseEvents")
                            && java.util.Set.of("allowMouseClick", "allowMouseRelease").contains(call.name))
                        found.merge(call.name, 1, Integer::sum);
                }
        assertEquals(java.util.Map.of("click", 1, "release", 1, "allowMouseClick", 1, "allowMouseRelease", 1), found);
    }

    @Test
    public void containerIconsUseOnlyPublicMinecraft21ResourceFactories() throws Exception {
        int factories = 0;
        for (var method : read("client/ui/DustbinUiSession").methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/resources/Identifier")) {
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
