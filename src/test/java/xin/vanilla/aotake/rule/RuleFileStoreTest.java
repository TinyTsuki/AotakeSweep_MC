package xin.vanilla.aotake.rule;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.Assert.*;

public class RuleFileStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void boundsPathsAndReadsAndNeverOverwritesExistingFiles() throws Exception {
        RuleFileStore store = new RuleFileStore(temporary.newFolder().toPath());
        byte[] bytes = "content".getBytes(StandardCharsets.UTF_8);
        store.writeNew("rules/sources/probe.java", bytes);
        store.writeNew("rules/sources/probe.java", bytes);
        assertThrows(Exception.class, () -> store.writeNew("rules/sources/probe.java", new byte[0]));
        assertArrayEquals(bytes, store.read("rules/sources/probe.java", 8));
        assertThrows(Exception.class, () -> store.read("rules/sources/probe.java", 3));
        for (String bad : new String[]{"../escape", "/absolute", "C:/absolute", "rules/../../escape", "rules\\..\\escape", "rules/file:stream"}) {
            assertThrows(bad, IllegalArgumentException.class, () -> store.writeNew(bad, bytes));
        }
        assertThrows(Exception.class, () -> store.replace("rules/sources/probe.java", new byte[0], bytes));
        assertArrayEquals(bytes, store.read("rules/sources/probe.java", 8));
        store.replace("rules/sources/probe.java", bytes, new byte[0]);
        assertEquals(0, store.read("rules/sources/probe.java", 8).length);
    }
    @Test public void refusesLinkedOwnerOrSubdirectory() throws Exception {
        Path root = temporary.newFolder().toPath(), outside = temporary.newFolder().toPath();
        Path link = root.resolve("linked");
        try { Files.createSymbolicLink(link, outside); }
        catch (java.io.IOException | UnsupportedOperationException denied) {
            if (!System.getProperty("os.name").startsWith("Windows")) throw denied;
            Process junction = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString()).redirectErrorStream(true).start();
            try { assertTrue("Junction creation timed out", junction.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, junction.exitValue()); }
            finally { if (junction.isAlive()) junction.destroyForcibly(); }
        }
        try {
            RuleFileStore store = new RuleFileStore(root);
            assertThrows(Exception.class, () -> store.writeNew("linked/escape.java", new byte[1]));
            assertThrows(Exception.class, () -> new RuleFileStore(link).writeNew("escape.java", new byte[1]));
            assertFalse(Files.exists(outside.resolve("escape.java")));
        } finally { Files.delete(link); }
    }
}
