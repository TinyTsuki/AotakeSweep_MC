package xin.vanilla.aotake.rule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.Assert.*;

public class BuiltinRulesTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void installsOnceWithoutOverwritingCustomizedJavaOrRepairingMissingFiles() throws Exception {
        RuleFileStore store = new RuleFileStore(temporary.newFolder().toPath());
        BuiltinRules.install(store);
        assertEquals(2, store.loadCatalog().sources().size());
        Path source = store.root().resolve(RuleCatalog.sourcePath(BuiltinRules.CLEANUP));
        byte[] edited = (new String(Files.readAllBytes(source), StandardCharsets.UTF_8) + "\n// customized\n").getBytes(StandardCharsets.UTF_8);
        Files.write(source, edited);
        BuiltinRules.install(store);
        assertArrayEquals(edited, Files.readAllBytes(source));
        Files.delete(source);
        assertThrows(Exception.class, () -> BuiltinRules.install(store));
        assertFalse(Files.exists(source));
    }
}
