package xin.vanilla.banira.internal.neoforge.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import static org.junit.Assert.*;

public class AotakeRuleConfigFixtureTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void nativeConfigurationDoesNotRetainFileHandlesAndCommitsSynchronously() throws Exception {
        Path path = temporary.newFolder().toPath().resolve("config.toml");
        try (AotakeRuleConfigFixture fixture = new AotakeRuleConfigFixture(path)) {
            Path moved = path.resolveSibling("moved.toml");
            Files.move(path, moved, StandardCopyOption.ATOMIC_MOVE);
            Files.move(moved, path, StandardCopyOption.ATOMIC_MOVE);
            Path replacement = path.resolveSibling("replacement.tmp");
            Files.write(replacement, Files.readAllBytes(path));
            Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            fixture.holder.set("unrelated", 2);
            assertEquals(Integer.valueOf(2), fixture.holder.get("unrelated"));
        }
    }
}
