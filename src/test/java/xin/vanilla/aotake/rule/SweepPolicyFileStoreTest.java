package xin.vanilla.aotake.rule;

import com.google.gson.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.api.rule.NativeMembers;
import xin.vanilla.aotake.api.sweep.SweepPolicy;
import xin.vanilla.banira.api.script.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class SweepPolicyFileStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private RuleFileStore store() throws IOException { return new RuleFileStore(temporary.newFolder().toPath()); }
    private void replace(RuleFileStore store, String path, String text) throws IOException {
        store.replace(path, store.optional(path, RuleFileStore.MAX_CONFIG_BYTES), text.getBytes(StandardCharsets.UTF_8));
    }
    static final String SOURCE = "rules/policies/sources/CustomSweepPolicy.java";

    @Test public void initializesOnceAndPreservesUserSourcesAndMissingFilesAreNotRecreated() throws Exception {
        RuleFileStore files = store(); SweepPolicyFileStore policies = new SweepPolicyFileStore(files);
        SweepPolicyCatalog initial = policies.load();
        assertEquals(Collections.singleton("custom"), initial.ids());
        byte[] manifest = files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES);
        replace(files, SOURCE, "// user-edited\n" + RuleFileStore.utf8(files.read(SOURCE, RuleFileStore.MAX_SOURCE_BYTES)));
        policies.load().verifyFiles();
        assertArrayEquals(manifest, files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
        assertTrue(RuleFileStore.utf8(files.read(SOURCE, RuleFileStore.MAX_SOURCE_BYTES)).startsWith("// user-edited"));
        Files.delete(files.path(SOURCE));
        assertThrows(IOException.class, policies::load);
        assertFalse(Files.exists(files.path(SOURCE)));
    }

    @Test public void existingSourceWithoutManifestIsPreservedAndConcurrentEditIsDetected() throws Exception {
        RuleFileStore files = store(); files.writeNew(SOURCE, "// personal source".getBytes(StandardCharsets.UTF_8));
        SweepPolicyCatalog catalog = new SweepPolicyFileStore(files).load();
        assertEquals("// personal source", RuleFileStore.utf8(files.read(SOURCE, 1024)));
        replace(files, SOURCE, "// changed during compilation");
        assertThrows(IOException.class, catalog::verifyFiles);
        assertEquals("// changed during compilation", RuleFileStore.utf8(files.read(SOURCE, 1024)));
    }

    @Test public void rejectsDuplicateIdsFieldsWrongVersionMissingEntrypointAndUnknownProperties() throws Exception {
        RuleFileStore files = store(); SweepPolicyFileStore policies = new SweepPolicyFileStore(files); policies.load();
        String baseline = RuleFileStore.utf8(files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
        JsonObject parsed = RuleFileStore.object(baseline.getBytes(StandardCharsets.UTF_8));
        JsonObject policy = parsed.getAsJsonArray("policies").get(0).getAsJsonObject();
        List<String> bad = Arrays.asList(
                baseline.replace("\"policySchema\": 1", "\"policySchema\": 2"),
                baseline.replace("\"apiVersion\": \"1\"", "\"apiVersion\": \"2\""),
                baseline.replace("\"policySchema\": 1", "\"policySchema\": 1, \"policySchema\": 1"),
                "{\"policySchema\":1,\"apiVersion\":\"1\",\"policies\":[" + policy + "," + policy + "]}",
                baseline.replace("CustomSweepPolicy.java", "Missing.java"),
                baseline.replace("\"entryClassName\"", "\"unknown\""));
        for (String invalid : bad) {
            assertNotEquals(baseline, invalid); replace(files, SweepPolicyFileStore.MANIFEST, invalid);
            assertThrows(IOException.class, policies::load);
            assertEquals(invalid, RuleFileStore.utf8(files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES)));
        }
    }

    @Test public void rejectsTraversal() throws Exception {
        RuleFileStore files = store(); SweepPolicyFileStore policies = new SweepPolicyFileStore(files); policies.load();
        String baseline = RuleFileStore.utf8(files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
        for (String path : Arrays.asList("../outside.java", "C:/outside.java", "/outside.java", "sub/../CustomSweepPolicy.java")) {
            replace(files, SweepPolicyFileStore.MANIFEST, baseline.replace("CustomSweepPolicy.java", path));
            assertThrows(IOException.class, policies::load);
        }
    }

    @Test public void rejectsLinkedSourceDirectoryWithoutWritingOutsideRoot() throws Exception {
        RuleFileStore files = store(); Path outside = temporary.newFolder().toPath();
        Path link = files.root().resolve("rules/policies/sources"); Files.createDirectories(link.getParent());
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException denied) {
            if (!System.getProperty("os.name").startsWith("Windows")) throw denied;
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), outside.toString()).redirectErrorStream(true).start();
            try { assertTrue(process.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, process.exitValue()); }
            finally { if (process.isAlive()) process.destroyForcibly(); }
        }
        try {
            assertThrows(Exception.class, () -> new SweepPolicyFileStore(files).load());
            try (java.util.stream.Stream<Path> children = Files.list(outside)) { assertEquals(0, children.count()); }
        } finally { Files.delete(link); }
    }

    @Test public void manifestMustNotExceedFileLimitsOrAcceptUnboundedNesting() throws Exception {
        RuleFileStore files = store(); SweepPolicyFileStore policies = new SweepPolicyFileStore(files); policies.load();
        StringBuilder json = new StringBuilder("{\"policySchema\":");
        for (int i = 0; i < 80; i++) json.append('[');
        json.append('1'); for (int i = 0; i < 80; i++) json.append(']'); json.append('}');
        replace(files, SweepPolicyFileStore.MANIFEST, json.toString());
        assertThrows(IOException.class, policies::load);
    }

    @Test public void preparesHelperSourcesAndResolvesMemberTypesWithoutConstructingPolicy() throws Exception {
        RuleFileStore files = store(); SweepPolicyFileStore policies = new SweepPolicyFileStore(files); policies.load();
        JsonObject manifest = RuleFileStore.object(files.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
        JsonObject policy = manifest.getAsJsonArray("policies").get(0).getAsJsonObject();
        policy.getAsJsonArray("sourceFiles").add("Clock.java");
        JsonObject member = new JsonObject();
        member.addProperty("id", "clock"); member.addProperty("kind", "METHOD"); member.addProperty("owner", "java.lang.System");
        member.addProperty("name", "currentTimeMillis"); member.addProperty("descriptor", "()J");
        member.addProperty("functionType", "xin.vanilla.banira.generated.policies.Clock");
        policy.getAsJsonArray("nativeMembers").add(member);
        replace(files, SweepPolicyFileStore.MANIFEST, manifest.toString());
        replace(files, SOURCE, "package xin.vanilla.banira.generated.policies; public class CustomSweepPolicy extends xin.vanilla.aotake.api.sweep.DefaultSweepPolicy<Void> { public CustomSweepPolicy(){throw new IllegalStateException(\"only at task creation\");} }");
        files.writeNew("rules/policies/sources/Clock.java", "package xin.vanilla.banira.generated.policies; public interface Clock { long read(); }".getBytes(StandardCharsets.UTF_8));
        Queue<Runnable> owner = new ConcurrentLinkedQueue<>();
        try (ScriptSession<ScriptFactory<SweepPolicy>> session = BaniraScripts.openFactorySession("aotake_sweep", SweepPolicy.class, "1", ScriptLimits.defaults(), owner::add)) {
            SweepPolicyCatalog catalog = policies.load();
            PreparedScripts<ScriptFactory<SweepPolicy>> compiled = await(catalog.prepare(session), owner);
            SweepPolicyCatalog.Prepared prepared = catalog.bind(compiled, (specs, loader) -> {
                assertEquals(1, specs.size()); assertEquals("clock", specs.get(0).id());
                assertSame(loader, specs.get(0).functionType().getClassLoader());
                return xin.vanilla.aotake.internal.forge.rule.ForgeNativeMembers.prepare(specs, loader, (kind, name) -> name);
            });
            assertEquals(2, prepared.ids().size()); assertTrue(prepared.ids().contains("builtin:default"));
            assertThrows(ScriptCompilationException.class, () -> prepared.create("custom"));
            assertNotSame(prepared.create("builtin:default"), prepared.create("builtin:default"));
            prepared.requireId("custom"); assertThrows(IllegalArgumentException.class, () -> prepared.requireId("../CustomSweepPolicy.java"));
            assertThrows(IllegalArgumentException.class, () -> prepared.requireId("missing"));
        }
    }

    static <T> T await(CompletableFuture<T> future, Queue<Runnable> owner) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!future.isDone() && System.nanoTime() < end) { Runnable r; while ((r = owner.poll()) != null) r.run(); Thread.sleep(1); }
        return future.get(1, TimeUnit.SECONDS);
    }
}
