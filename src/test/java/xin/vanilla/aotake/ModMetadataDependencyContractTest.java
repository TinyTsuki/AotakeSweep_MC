package xin.vanilla.aotake;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.Test;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the dependency graph emitted into the distributable Forge metadata.
 */
public class ModMetadataDependencyContractTest {
    private static final String MOD_ID = "aotake_sweep";
    private static final Path PROCESSED_METADATA =
            Paths.get("build/resources/main/META-INF/mods.toml");

    @Test
    public void dependencyGraphDoesNotContainCurrentMod() throws Exception {
        List<? extends Config> dependencies = dependencies();

        assertFalse("A mod dependency on itself creates a cyclic loader graph",
                dependencies.stream().anyMatch(this::isCurrentMod));
        assertTrue("Banira Codex must remain a mandatory dependency",
                dependencies.stream().anyMatch(this::isMandatoryBaniraDependency));
    }

    private List<? extends Config> dependencies() throws Exception {
        // Parse processed resources so Gradle variable expansion is covered.
        try (Reader reader = Files.newBufferedReader(PROCESSED_METADATA, StandardCharsets.UTF_8)) {
            Config metadata = new TomlParser().parse(reader);
            return metadata.get("dependencies." + MOD_ID);
        }
    }

    private boolean isCurrentMod(Config dependency) {
        return MOD_ID.equals(dependency.<String>get("modId"));
    }

    private boolean isMandatoryBaniraDependency(Config dependency) {
        return "banira_codex".equals(dependency.<String>get("modId"))
                && Boolean.TRUE.equals(dependency.get("mandatory"));
    }
}
