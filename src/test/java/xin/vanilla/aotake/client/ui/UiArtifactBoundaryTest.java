package xin.vanilla.aotake.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class UiArtifactBoundaryTest {
    static List<String> violations(Map<String, byte[]> files, boolean merged) {
        return UiBoundaryChecks.artifact(files, "xin/vanilla/aotake/", merged);
    }

    @Test
    public void ownedClassIsAllowedAndEmbeddedSdkRequiresMergedArchive() {
        lombok.var writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(52, 1, "xin/vanilla/aotake/Example", null, "java/lang/Object", null);
        writer.visitEnd();
        assertTrue(violations(com.google.common.collect.ImmutableMap.of("xin/vanilla/aotake/Example.class", writer.toByteArray()), false).isEmpty());
        writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(52, 1, "xin/vanilla/banira/Example", null, "java/lang/Object", null);
        writer.visitEnd();
        assertFalse(violations(com.google.common.collect.ImmutableMap.of("xin/vanilla/banira/Example.class", writer.toByteArray()), false).isEmpty());
        assertTrue(violations(com.google.common.collect.ImmutableMap.of("xin/vanilla/banira/Example.class", writer.toByteArray()), true).isEmpty());
    }

    @Test
    public void engineStubTestProbeAndSourceLeaksAreRejected() {
        for (String path : com.google.common.collect.ImmutableList.of("com/sighs/apricityui/Engine.class", "com/sighs/apricityui/Engine.java",
                "META-INF/banira-aui-compile-api.properties", "xin/vanilla/aotake/InjectedTest.class", "xin/vanilla/aotake/RuntimeProbe.class", "docs/task.md"))
            assertFalse(path, violations(com.google.common.collect.ImmutableMap.of(path, new byte[0]), false).isEmpty());
    }

    @Test
    public void nestedEngineAndPomLeaksAreRejected() throws Exception {
        assertTrue(UiBoundaryChecks.pom("<project/>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertFalse(UiBoundaryChecks.pom("<project><dependencies><dependency><artifactId>apricityui</artifactId></dependency></dependencies></project>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        lombok.var output = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("com/sighs/apricityui/Engine.class"));
            zip.write(new byte[0]);
            zip.closeEntry();
        }
        assertFalse(violations(com.google.common.collect.ImmutableMap.of("META-INF/jarjar/engine.jar", output.toByteArray()), true).isEmpty());
    }

    @Test
    public void futureClassesAndUnsafePathsAreRejected() {
        byte[] header = {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0, 0, 0, 53};
        assertFalse(violations(com.google.common.collect.ImmutableMap.of("xin/vanilla/aotake/Future.class", header), false).isEmpty());
        for (String path : com.google.common.collect.ImmutableList.of("../escape", "assets/../foreign", "C:/foreign"))
            assertFalse(violations(com.google.common.collect.ImmutableMap.of(path, new byte[0]), false).isEmpty());
    }

    @Test
    public void formalArchivesContainOnlyProductFiles() throws Exception {
        String inputs = System.getProperty("banira.ui.artifacts");
        assertNotNull("uiArtifactTest must supply archive providers", inputs);
        for (String file : inputs.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            assertTrue(file, Files.isRegularFile(java.nio.file.Paths.get(file)));
            lombok.var files = UiBoundaryChecks.readZip(java.nio.file.Paths.get(file));
            assertEquals(file, com.google.common.collect.ImmutableList.of(), violations(files, file.endsWith("-all.jar")));
            if (java.nio.file.Paths.get(file).getFileName().toString().contains("-fabric-1.16.5-")
                    && !file.endsWith("-sources.jar")) verifyFabricContainerMappings(files);
        }
        String poms = System.getProperty("banira.ui.poms", "");
        if (!poms.isEmpty()) for (String pom : poms.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            assertEquals(pom, com.google.common.collect.ImmutableList.of(), UiBoundaryChecks.pom(Files.readAllBytes(java.nio.file.Paths.get(pom))));
    }

    private static void verifyFabricContainerMappings(Map<String, byte[]> files) {
        String[][] targets = {
                {"ContainerScreenMixin", "aotake$renderUiLabels"},
                {"ContainerScreenMixin", "aotake$releaseUiAppearance"},
                {"ChestScreenMixin", "aotake$interceptRenderBg"}
        };
        for (String[] target : targets) {
            byte[] bytes = files.get("xin/vanilla/aotake/mixin/" + target[0] + ".class");
            assertNotNull(target[0], bytes);
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
            org.objectweb.asm.tree.MethodNode method = node.methods.stream().filter(value -> value.name.equals(target[1]))
                    .findFirst().orElseThrow(AssertionError::new);
            java.util.List<org.objectweb.asm.tree.AnnotationNode> annotations = new java.util.ArrayList<>();
            if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
            if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
            boolean found = false;
            for (org.objectweb.asm.tree.AnnotationNode annotation : annotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                for (int i = 0; i < annotation.values.size(); i += 2) {
                    if (!annotation.values.get(i).equals("method")) continue;
                    java.util.List<?> selectors = (java.util.List<?>) annotation.values.get(i + 1);
                    assertFalse(target[1], selectors.isEmpty());
                    for (Object selector : selectors) {
                        String value = selector.toString();
                        assertTrue(target[1] + ": " + value, value.startsWith("method_"));
                        assertFalse(value, value.contains("com/mojang/blaze3d/vertex/PoseStack"));
                    }
                    found = true;
                }
            }
            assertTrue(target[1] + " missing mapped injection selectors", found);
        }
    }

    @Test
    public void unregisteredDevelopmentFixturesAreRejected() throws Exception {
        for (String path : new String[]{"xin/vanilla/aotake/internal/dev/Fixture.java",
                "xin/vanilla/aotake/internal/client/dev/Fixture.java",
                "xin/vanilla/aotake/internal/server/dev/Fixture.java"}) {
            assertTrue(violations(com.google.common.collect.ImmutableMap.of(path, new byte[0]), false)
                    .contains("Non-product entry " + path));
        }
    }

    @Test
    public void originalFabricDiagnosticsStillRequireValidJava8ClassIdentity() {
        String type = "xin/vanilla/aotake/internal/client/dev/AotakeUiSmokeRunner";
        org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(52, 1, type, null, "java/lang/Object", null);
        writer.visitEnd();
        assertTrue(violations(com.google.common.collect.ImmutableMap.of(type + ".class", writer.toByteArray()), false).isEmpty());
        assertFalse(violations(com.google.common.collect.ImmutableMap.of(type + ".class", new byte[0]), false).isEmpty());
        writer = new org.objectweb.asm.ClassWriter(0);
        writer.visit(53, 1, type, null, "java/lang/Object", null);
        writer.visitEnd();
        assertFalse(violations(com.google.common.collect.ImmutableMap.of(type + ".class", writer.toByteArray()), false).isEmpty());
    }

    @Test
    public void opaqueEngineCoordinatesAreRejected() {
        String xml = "<project><dependencies><dependency><groupId>maven.modrinth</groupId><artifactId>UfPdUeGy</artifactId></dependency></dependencies></project>";
        assertFalse(UiBoundaryChecks.pom(xml.getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    public void namespacedEngineDependenciesAreRejected() {
        String xml = "<m:project xmlns:m='http://maven.apache.org/POM/4.0.0'><m:dependencies><m:dependency><m:artifactId>apricityui</m:artifactId></m:dependency></m:dependencies></m:project>";
        assertFalse(UiBoundaryChecks.pom(xml.getBytes(StandardCharsets.UTF_8)).isEmpty());
    }

    @Test
    public void localAgentInstructionsAreRejected() {
        assertFalse(violations(com.google.common.collect.ImmutableMap.of("AGENTS.md", new byte[0]), false).isEmpty());
    }
}
