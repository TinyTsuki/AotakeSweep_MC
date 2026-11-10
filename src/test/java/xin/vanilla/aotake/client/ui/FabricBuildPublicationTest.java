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
    public void validationPublishesBothRemappedArchives() throws Exception {
        String publication = source("gradle/aui-validation-publication.gradle");
        assertTrue(publication.contains("setArtifacts([tasks.named('remapJar'), tasks.named('remapSourcesJar')])"));
        assertFalse(publication.contains("tasks.named('sourcesJar')"));
        assertTrue(publication.contains("gradle.projectsEvaluated"));
        assertTrue(publication.contains("pom.withXml"));
    }


}
