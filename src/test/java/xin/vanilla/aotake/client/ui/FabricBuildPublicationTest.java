package xin.vanilla.aotake.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class FabricBuildPublicationTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void configurationCannotWriteClientFpsOptions() throws Exception {
        String build = source("build.gradle");
        assertFalse(Pattern.compile("(?m)^\\s*prepareBaniraDevFpsOverlay\\(\\)\\s*$").matcher(build).find());
        assertTrue(build.contains("doLast prepareBaniraDevFpsOverlay"));
    }

    @Test
    public void clientRunStillPreparesTheDevelopmentOverlay() throws Exception {
        assertTrue(Pattern.compile("tasks\\.named\\('runClient'\\)\\.configure\\s*\\{\\s*dependsOn\\s+'prepareDevFpsOverlay'\\s*}")
                .matcher(source("build.gradle")).find());
    }

    @Test
    public void validationPublishesBothRemappedArchives() throws Exception {
        String publication = source("gradle/aui-validation-publication.gradle");
        assertTrue(publication.contains("setArtifacts([tasks.named('remapJar'), tasks.named('remapSourcesJar')])"));
        assertFalse(publication.contains("tasks.named('sourcesJar')"));
        assertTrue(publication.contains("gradle.projectsEvaluated"));
        assertTrue(publication.contains("pom.withXml"));
    }

    @Test
    public void validationOverrideRetainsTheDefaultPublicationDeclaration() throws Exception {
        assertTrue(source("gradle/aui-validation-publication.gradle").stripLeading()
                .startsWith("if (providers.gradleProperty('banira.validationOutputRoot').isPresent()) {"));
        assertTrue(source("build.gradle").contains("from components.java"));
    }
}
