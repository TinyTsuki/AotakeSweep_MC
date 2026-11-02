package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Fabric18UiPortTest {
    private ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (var input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }

    private boolean calls(ClassNode type, String owner, String name) {
        return type.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.endsWith(owner) && call.name.equals(name));
    }

    @Test
    public void fabricMouseObserversUseTheSingleSessionAndKeepExistingCallbacks() throws Exception {
        var entry = node("internal/fabric/client/FabricAotakeClientEntry");
        assertTrue(calls(entry, "client/ui/AotakeUiPages", "click"));
        assertTrue(calls(entry, "client/ui/AotakeUiPages", "release"));
        assertTrue(calls(entry, "client/screen/v1/ScreenMouseEvents", "allowMouseClick"));
        assertTrue(calls(entry, "client/screen/v1/ScreenMouseEvents", "allowMouseRelease"));
        assertTrue(calls(entry, "screen/DustbinRender", "handleGuiScreen"));
        assertTrue(calls(node("client/AotakeClientBootstrap"), "client/ui/AotakeUiPages", "register"));
    }

    @Test
    public void mixinsOnlyReplaceAppearanceAndReleaseItsOwnership() throws Exception {
        assertTrue(calls(node("mixin/ChestScreenMixin"), "client/ui/AotakeUiPages", "renderBackground"));
        var container = node("mixin/ContainerScreenMixin");
        assertTrue(calls(container, "client/ui/AotakeUiPages", "owns"));
        assertTrue(calls(container, "client/ui/AotakeUiPages", "close"));
        for (var method : container.methods)
            if (method.name.contains("Ui"))
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call)
                        assertFalse(call.name.equals("clicked") || call.name.equals("quickMoveStack") || call.name.equals("slotClicked"));
    }

    @Test
    public void originalMouseStateAndPlayerSaveWireStayIntact() throws Exception {
        var render = node("screen/DustbinRender");
        assertTrue(calls(render, "internal/client/DustbinMouseInput", "poll"));
        assertTrue(calls(render, "internal/client/DustbinMouseInput", "rawCursorPosition"));
        assertTrue(calls(render, "screen/DustbinToolbarTooltipRegistry", "find"));
        var packet = node("network/packet/PlayerConfigSyncToServer");
        assertEquals(2, packet.methods.stream().filter(method -> method.name.equals("toBytes"))
                .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("writeBoolean")).count());
        assertTrue(calls(node("screen/PlayerConfigScreen"), "common/util/PacketUtils", "sendPacketToServer"));
    }

    @Test
    public void isolatedBuildCannotWriteDistributionOrClientSettings() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("destinationDirectory.set(baniraDistributionDirectory)"));
        assertTrue(build.contains("artifact tasks.named('remapSourcesJar')"));
        assertFalse(build.contains("artifact tasks.named('sourcesJar')"));
        assertTrue(build.contains("tasks.named('runClient').configure {\n    dependsOn 'prepareDevFpsOverlay'")
                || build.contains("tasks.named('runClient').configure {\r\n    dependsOn 'prepareDevFpsOverlay'"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^prepareBaniraDevFpsOverlay\\(\\)\\s*$").matcher(build).find());
        String isolation = Files.readString(Path.of("gradle/aui-validation-output.gradle"));
        assertTrue(isolation.contains("disallowChanges()"));
        assertTrue(isolation.contains("Unsafe validation output root"));
        assertTrue(isolation.contains("Publication escaped validation output root"));
        for (String name : new String[]{"client/ui/AotakeUiPages", "client/ui/DustbinUiSession"})
            for (var method : node(name).methods)
                for (var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode call)
                        assertFalse(call.owner.startsWith("net/minecraftforge/") || call.owner.startsWith("com/sighs/"));
    }

    @Test
    public void nativeAppearanceAndTooltipsUseThePoseStackContract() throws Exception {
        String source = Files.readString(Path.of("src/main/java/xin/vanilla/aotake/client/ui/AotakeUiPages.java"));
        assertTrue(source.contains("I18n.get(\"container.inventory\")"));
        assertFalse(source.contains("net.minecraft.network.chat.Component.translatable"));
        var pages = node("client/ui/AotakeUiPages");
        assertTrue(pages.methods.stream().anyMatch(method -> method.name.equals("renderBackground")
                && method.desc.equals("(Lnet/minecraft/client/gui/screens/inventory/ContainerScreen;Lcom/mojang/blaze3d/vertex/PoseStack;)Z")));
        assertTrue(calls(pages, "net/minecraft/client/gui/screens/Screen", "renderTooltip"));
        var mixin = node("mixin/ChestScreenMixin");
        assertTrue(mixin.methods.stream().anyMatch(method -> method.name.equals("aotake$interceptRenderBg")
                && method.desc.startsWith("(Lcom/mojang/blaze3d/vertex/PoseStack;FII")));
        var progress = node("screen/ProgressRender");
        assertTrue(calls(progress, "com/mojang/math/Vector3f", "rotation"));
        for (var method : progress.methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call)
                    assertFalse(call.owner.startsWith("org/joml/") || call.owner.equals("net/minecraft/client/gui/GuiGraphics"));
    }
}
