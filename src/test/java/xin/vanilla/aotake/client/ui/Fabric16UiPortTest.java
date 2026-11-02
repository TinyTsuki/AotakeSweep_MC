package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.*;

public class Fabric16UiPortTest {
    @Test
    public void containerInjectionTargetsAreDeclaredByTheActualMinecraft16Classes() throws Exception {
        String[][] targets = {
                {"net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "init", "()V"},
                {"net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "removed", "()V"},
                {"net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "renderLabels", "(Lcom/mojang/blaze3d/vertex/PoseStack;II)V"},
                {"net/minecraft/client/gui/screens/inventory/ContainerScreen", "renderBg", "(Lcom/mojang/blaze3d/vertex/PoseStack;FII)V"}
        };
        for (String[] target : targets) {
            ClassNode type = new ClassNode();
            try (java.io.InputStream input = getClass().getResourceAsStream("/" + target[0] + ".class")) {
                assertNotNull(target[0], input);
                new ClassReader(input).accept(type, ClassReader.SKIP_CODE);
            }
            assertTrue(target[0] + "#" + target[1], type.methods.stream()
                    .anyMatch(method -> method.name.equals(target[1]) && method.desc.equals(target[2])));
        }
    }

    private ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (lombok.var input = getClass().getResourceAsStream("/xin/vanilla/aotake/" + name + ".class")) {
            assertNotNull(name, input);
            new ClassReader(input).accept(result, 0);
        }
        return result;
    }

    private boolean calls(ClassNode type, String owner, String name) {
        return type.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .anyMatch(instruction -> instruction instanceof MethodInsnNode
                        && ((MethodInsnNode) instruction).owner.endsWith(owner) && ((MethodInsnNode) instruction).name.equals(name));
    }

    @Test
    public void fabricMouseObserversUseTheSingleSessionAndKeepExistingCallbacks() throws Exception {
        lombok.var entry = node("internal/fabric/client/FabricAotakeClientEntry");
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
        lombok.var container = node("mixin/ContainerScreenMixin");
        assertTrue(calls(container, "client/ui/AotakeUiPages", "owns"));
        assertTrue(calls(container, "client/ui/AotakeUiPages", "close"));
        for (lombok.var method : container.methods)
            if (method.name.contains("Ui"))
                for (lombok.var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode)
                        assertFalse(((MethodInsnNode) instruction).name.equals("clicked") || ((MethodInsnNode) instruction).name.equals("quickMoveStack") || ((MethodInsnNode) instruction).name.equals("slotClicked"));
    }

    @Test
    public void originalMouseStateAndPlayerSaveWireStayIntact() throws Exception {
        lombok.var render = node("screen/DustbinRender");
        assertTrue(calls(render, "internal/client/DustbinMouseInput", "poll"));
        assertTrue(calls(render, "internal/client/DustbinMouseInput", "rawCursorPosition"));
        assertTrue(calls(render, "screen/DustbinToolbarTooltipRegistry", "find"));
        lombok.var packet = node("network/packet/PlayerConfigSyncToServer");
        assertEquals(2, packet.methods.stream().filter(method -> method.name.equals("toBytes"))
                .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .filter(instruction -> instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("writeBoolean")).count());
        assertTrue(calls(node("screen/PlayerConfigScreen"), "common/util/PacketUtils", "sendPacketToServer"));
    }

    @Test
    public void isolatedBuildCannotWriteDistributionOrClientSettings() throws Exception {
        String build = UiBoundaryChecks.text(java.nio.file.Paths.get("build.gradle"));
        assertTrue(build.contains("destinationDirectory.set(baniraDistributionDirectory)"));
        assertTrue(build.contains("artifact tasks.named('remapSourcesJar')"));
        assertFalse(build.contains("artifact tasks.named('sourcesJar')"));
        assertTrue(build.contains("tasks.named('runClient').configure {\n    dependsOn 'prepareDevFpsOverlay'")
                || build.contains("tasks.named('runClient').configure {\r\n    dependsOn 'prepareDevFpsOverlay'"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^prepareBaniraDevFpsOverlay\\(\\)\\s*$").matcher(build).find());
        String isolation = UiBoundaryChecks.text(java.nio.file.Paths.get("gradle/aui-validation-output.gradle"));
        assertTrue(isolation.contains("disallowChanges()"));
        assertTrue(isolation.contains("Unsafe validation output root"));
        assertTrue(isolation.contains("Publication escaped validation output root"));
        for (String name : new String[]{"client/ui/AotakeUiPages", "client/ui/DustbinUiSession"})
            for (lombok.var method : node(name).methods)
                for (lombok.var instruction : method.instructions)
                    if (instruction instanceof MethodInsnNode)
                        assertFalse(((MethodInsnNode) instruction).owner.startsWith("net/minecraftforge/") || ((MethodInsnNode) instruction).owner.startsWith("com/sighs/"));
    }

    @Test
    public void nativeAppearanceAndTooltipsUseThePoseStackContract() throws Exception {
        String source = UiBoundaryChecks.text(java.nio.file.Paths.get("src/main/java/xin/vanilla/aotake/client/ui/AotakeUiPages.java"));
        assertTrue(source.contains("new net.minecraft.network.chat.TranslatableComponent(\"container.inventory\").getString()"));
        assertFalse(source.contains("net.minecraft.network.chat.Component.translatable"));
        lombok.var pages = node("client/ui/AotakeUiPages");
        assertTrue(pages.methods.stream().anyMatch(method -> method.name.equals("renderBackground")
                && method.desc.equals("(Lnet/minecraft/client/gui/screens/inventory/ContainerScreen;Lcom/mojang/blaze3d/vertex/PoseStack;)Z")));
        assertTrue(calls(pages, "net/minecraft/client/gui/screens/Screen", "renderTooltip"));
        lombok.var mixin = node("mixin/ChestScreenMixin");
        assertTrue(mixin.methods.stream().anyMatch(method -> method.name.equals("aotake$interceptRenderBg")
                && method.desc.startsWith("(Lcom/mojang/blaze3d/vertex/PoseStack;FII")));
        lombok.var progress = node("screen/ProgressRender");
        assertTrue(calls(progress, "com/mojang/math/Vector3f", "rotation"));
        for (lombok.var method : progress.methods)
            for (lombok.var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode)
                    assertFalse(((MethodInsnNode) instruction).owner.startsWith("org/joml/") || ((MethodInsnNode) instruction).owner.equals("net/minecraft/client/gui/GuiGraphics"));
    }
}
