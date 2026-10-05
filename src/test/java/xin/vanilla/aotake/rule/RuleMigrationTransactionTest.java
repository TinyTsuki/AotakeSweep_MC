package xin.vanilla.aotake.rule;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.common.config.*;
import xin.vanilla.banira.internal.neoforge.config.AotakeRuleConfigFixture;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuleMigrationTransactionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    static final String EXPRESSION = "tick -> tick >= 5";

    @Test public void migratesFourListsOncePreservingOrderModesAndBackup() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            ConfigEditSnapshot before = f.snapshot();
            AtomicInteger saves = new AtomicInteger(); f.holder.onSaved(paths -> saves.incrementAndGet());
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            assertArrayEquals(before.getSourceBytes(), Files.readAllBytes(f.config));
            assertEquals(1, candidate.catalog().sources().size());
            assertEquals(ConfigCommitResult.APPLIED, f.transaction.commit(candidate));
            String id = candidate.catalog().sources().keySet().iterator().next();
            for (String path : RuleMigrationTransaction.LISTS) {
                assertEquals(Arrays.asList("minecraft:arrow", "script:" + id, "minecraft:*") , f.holder.get(path));
            }
            assertEquals("WHITE", f.holder.get(RuleMigrationTransaction.MODES.get(0)));
            assertEquals(1, saves.get());
            assertArrayEquals(before.getSourceBytes(), f.store.read("backups/rule-migration/" + candidate.id() + "/original.config", RuleFileStore.MAX_CONFIG_BYTES));
            com.google.gson.JsonObject report = RuleFileStore.object(f.store.read("backups/rule-migration/" + candidate.id() + "/report.json", RuleFileStore.MAX_CONFIG_BYTES));
            assertEquals(RuleFileStore.hash(candidate.catalog().manifestBytes()), report.get("targetManifestHash").getAsString());
            for (Map.Entry<String,byte[]> file : candidate.catalog().files().entrySet()) {
                assertEquals(RuleFileStore.hash(file.getValue()), report.getAsJsonObject("fileHashes").get(file.getKey()).getAsString());
            }
            assertTrue(f.session.active().isEmpty()); // Publication belongs to RuleManager.
            byte[] source = f.store.read("rules/sources/" + id + ".java", RuleFileStore.MAX_SOURCE_BYTES);
            assertEquals(ConfigCommitResult.UNCHANGED, f.transaction.commit(f.prepare()));
            assertArrayEquals(source, f.store.read("rules/sources/" + id + ".java", RuleFileStore.MAX_SOURCE_BYTES));
            assertEquals(1, saves.get());
        }
    }

    @Test public void everyPersistenceBoundaryCanRecoverWithoutLosingConfiguration() throws Exception {
        for (RuleMigrationTransaction.Checkpoint point : RuleMigrationTransaction.Checkpoint.values()) {
            try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
                byte[] original = Files.readAllBytes(f.config);
                f.transaction = f.transaction(checkpoint -> { if (checkpoint == point) throw new IllegalStateException("power loss " + point); });
                try { f.transaction.commit(f.prepare()); fail("Missing checkpoint " + point); }
                catch (Exception expected) { assertTrue(root(expected).getMessage(), root(expected).getMessage().contains("power loss")); }
                f.transaction = f.transaction(checkpoint -> {});
                RuleMigrationTransaction.RecoveryResult recovery = f.transaction.recover();
                assertNotEquals(point + ": " + recovery.diagnostic(), RuleMigrationTransaction.RecoveryState.BLOCKED, recovery.state());
                boolean committed = point == RuleMigrationTransaction.Checkpoint.AFTER_CONFIG || point == RuleMigrationTransaction.Checkpoint.BEFORE_COMMITTED;
                if (!committed) assertArrayEquals(point.toString(), original, Files.readAllBytes(f.config));
                f.transaction.commit(f.prepare());
                for (String path : RuleMigrationTransaction.LISTS) assertTrue(((List<?>) f.holder.get(path)).get(1).toString().startsWith("script:"));
            }
        }
    }

    @Test public void externalConfigAndGeneratedFileEditsAreNotOverwritten() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            byte[] external = (new String(Files.readAllBytes(f.config), StandardCharsets.UTF_8) + "\n# administrator edit\n").getBytes(StandardCharsets.UTF_8);
            Files.write(f.config, external);
            assertEquals(ConfigCommitResult.CONFLICT, f.transaction.commit(candidate));
            assertArrayEquals(external, Files.readAllBytes(f.config));
            assertEquals(RuleMigrationTransaction.RecoveryState.BLOCKED, f.transaction.recover().state());
        }
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            String id = candidate.catalog().sources().keySet().iterator().next();
            Path path = f.store.root().resolve("rules/sources/" + id + ".java");
            Files.createDirectories(path.getParent()); byte[] manual = "// administrator file".getBytes(StandardCharsets.UTF_8); Files.write(path, manual);
            byte[] before = Files.readAllBytes(f.config);
            assertThrows(Exception.class, () -> f.transaction.commit(candidate));
            assertArrayEquals(manual, Files.readAllBytes(path));
            assertArrayEquals(before, Files.readAllBytes(f.config));
            assertEquals(RuleMigrationTransaction.RecoveryState.BLOCKED, f.transaction.recover().state());
        }
    }

    @Test public void sourceChangesDuringCompilationAndStaleCandidatesCannotCommit() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration first = f.prepare();
            RuleMigrationTransaction.PreparedMigration second = f.prepare();
            assertThrows(IllegalStateException.class, () -> f.transaction.commit(first));
            f.transaction.commit(second);
            RuleMigrationTransaction.PreparedMigration next = f.prepare();
            String id = next.catalog().sources().keySet().iterator().next();
            Path source = f.store.root().resolve("rules/sources/" + id + ".java");
            Files.write(source, "// changed after compilation".getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () -> f.transaction.commit(next));
            assertTrue(new String(Files.readAllBytes(source), StandardCharsets.UTF_8).contains("changed after"));
        }
    }

    @Test public void compileAndBindingFailuresKeepOriginalListsAndFiles() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            f.holder.set(RuleMigrationTransaction.LISTS.get(3), Collections.singletonList("tick -> tick == '5'"));
            byte[] before = Files.readAllBytes(f.config);
            assertThrows(Exception.class, f::prepare);
            assertArrayEquals(before, Files.readAllBytes(f.config));
            assertFalse(Files.exists(f.store.root().resolve("rules/manifest.json")));
        }
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            f.transaction = new RuleMigrationTransaction(f.store, f.holder, f.session, plan -> { throw new IllegalArgumentException("binding unavailable"); });
            byte[] before = Files.readAllBytes(f.config);
            assertThrows(Exception.class, f::prepare);
            assertArrayEquals(before, Files.readAllBytes(f.config));
        }
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            f.transaction.commit(f.prepare());
            String id = f.store.loadCatalog().sources().keySet().iterator().next();
            Path source = f.store.root().resolve("rules/sources/" + id + ".java");
            Files.write(source, "invalid java".getBytes(StandardCharsets.UTF_8));
            byte[] before = Files.readAllBytes(f.config);
            assertThrows(Exception.class, f::prepare);
            assertArrayEquals(before, Files.readAllBytes(f.config));
            assertEquals("invalid java", new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
        }
    }

    @Test public void modeChangesAndWrongTransactionOwnerInvalidateCandidate() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath()); Fixture other = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            assertThrows(IllegalArgumentException.class, () -> other.transaction.commit(candidate));
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            assertEquals(ConfigCommitResult.CONFLICT, f.transaction.commit(candidate));
            assertEquals("BLACK", f.holder.get(RuleMigrationTransaction.MODES.get(0)));
            assertEquals(Arrays.asList("minecraft:arrow", EXPRESSION, "minecraft:*"), f.holder.get(RuleMigrationTransaction.LISTS.get(0)));
        }
    }

    @Test public void restartRecoversCommittedReferencesAndPreservesEditedSourceAndManifestFormatting() throws Exception {
        Path directory = temporary.newFolder().toPath(); String id;
        try (Fixture f = new Fixture(directory)) {
            f.transaction = f.transaction(point -> { if (point == RuleMigrationTransaction.Checkpoint.AFTER_CONFIG) throw new IllegalStateException("power loss"); });
            RuleMigrationTransaction.PreparedMigration prepared = f.prepare(); id = prepared.catalog().sources().keySet().iterator().next();
            assertThrows(IllegalStateException.class, () -> f.transaction.commit(prepared));
        }
        try (Fixture f = new Fixture(directory)) {
            assertEquals(RuleMigrationTransaction.RecoveryState.READY, f.transaction.recover().state());
            Path source = f.store.root().resolve("rules/sources/" + id + ".java"), manifest = f.store.root().resolve("rules/manifest.json");
            String edited = new String(Files.readAllBytes(source), StandardCharsets.UTF_8).replace(">=5", ">=9");
            assertFalse("Fixture must change the condition", edited.equals(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)));
            Files.write(source, edited.getBytes(StandardCharsets.UTF_8));
            Files.write(manifest, ("\n" + new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8));
            byte[] expectedManifest = Files.readAllBytes(manifest);
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            LegacyRuleMigrationTest.CountingContext context = new LegacyRuleMigrationTest.CountingContext();
            RuleBindingPlan plan = candidate.catalog().sources().get(id).bindings();
            assertFalse(context.evaluate(candidate.compiled().scripts().get(id), Collections.singletonMap("tick", 7), plan));
            assertTrue(context.evaluate(candidate.compiled().scripts().get(id), Collections.singletonMap("tick", 10), plan));
            assertEquals(ConfigCommitResult.UNCHANGED, f.transaction.commit(candidate));
            assertArrayEquals(edited.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(source));
            assertArrayEquals(expectedManifest, Files.readAllBytes(manifest));
        }
    }

    @Test public void committedMissingFilesRecoverFromVerifiedSnapshotWithoutChangingConfig() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration prepared = f.prepare(); f.transaction.commit(prepared);
            byte[] config = Files.readAllBytes(f.config);
            byte[] manifest = f.store.read(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES);
            for (String path : prepared.catalog().files().keySet()) Files.delete(f.store.root().resolve(path));
            assertEquals(RuleMigrationTransaction.RecoveryState.READY, f.transaction.recover().state());
            for (Map.Entry<String,byte[]> file : prepared.catalog().files().entrySet())
                assertArrayEquals(file.getValue(), f.store.read(file.getKey(), RuleFileStore.MAX_SOURCE_BYTES));
            assertArrayEquals(config, Files.readAllBytes(f.config));
            assertArrayEquals(manifest, f.store.read(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
            assertEquals(ConfigCommitResult.UNCHANGED, f.transaction.commit(f.prepare()));
        }
    }

    @Test public void missingSourceRecoveryPreservesAnEditedBinding() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration prepared = f.prepare(); f.transaction.commit(prepared);
            String id = prepared.catalog().sources().keySet().iterator().next();
            byte[] binding = f.store.read(RuleCatalog.bindingPath(id), RuleFileStore.MAX_SOURCE_BYTES);
            byte[] edited = (new String(binding, StandardCharsets.UTF_8) + "\n ").getBytes(StandardCharsets.UTF_8);
            Files.write(f.store.root().resolve(RuleCatalog.bindingPath(id)), edited);
            Files.delete(f.store.root().resolve(RuleCatalog.sourcePath(id)));
            assertEquals(RuleMigrationTransaction.RecoveryState.READY, f.transaction.recover().state());
            assertArrayEquals(edited, f.store.read(RuleCatalog.bindingPath(id), RuleFileStore.MAX_SOURCE_BYTES));
        }
    }

    @Test public void corruptRecoverySnapshotCannotPartiallyRestoreMissingRules() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration prepared = f.prepare(); f.transaction.commit(prepared);
            String id = prepared.catalog().sources().keySet().iterator().next();
            for (String path : prepared.catalog().files().keySet()) Files.delete(f.store.root().resolve(path));
            Files.write(f.store.root().resolve("rules/.staging/" + prepared.id() + "/" + RuleCatalog.bindingPath(id)), new byte[]{1});
            assertEquals(RuleMigrationTransaction.RecoveryState.BLOCKED, f.transaction.recover().state());
            for (String path : prepared.catalog().files().keySet()) assertFalse(Files.exists(f.store.root().resolve(path)));
        }
    }

    @Test public void changedManifestDoesNotRestoreDeletedRulesFromAnOlderSnapshot() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            RuleMigrationTransaction.PreparedMigration prepared = f.prepare(); f.transaction.commit(prepared);
            String id = prepared.catalog().sources().keySet().iterator().next();
            Files.delete(f.store.root().resolve(RuleCatalog.sourcePath(id)));
            Path manifest = f.store.root().resolve(RuleFileStore.MANIFEST);
            byte[] edited = (new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8) + "\n ").getBytes(StandardCharsets.UTF_8);
            Files.write(manifest, edited);
            assertEquals(RuleMigrationTransaction.RecoveryState.NOT_NEEDED, f.transaction.recover().state());
            assertThrows(Exception.class, f::prepare);
            assertFalse(Files.exists(f.store.root().resolve(RuleCatalog.sourcePath(id))));
            assertArrayEquals(edited, Files.readAllBytes(manifest));
        }
    }

    @Test public void backupIoFailureAndDamagedPreparedFilesBlockWithoutOverwritingAnything() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            Files.createDirectories(f.store.root()); Files.write(f.store.root().resolve("backups"), new byte[]{1});
            byte[] before = Files.readAllBytes(f.config); assertThrows(Exception.class, f::prepare);
            assertArrayEquals(before, Files.readAllBytes(f.config));
            assertFalse(Files.exists(f.store.root().resolve("rules/manifest.json")));
        }
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            f.transaction = f.transaction(point -> { if (point == RuleMigrationTransaction.Checkpoint.AFTER_CONFIG) throw new IllegalStateException("power loss"); });
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare(); assertThrows(Exception.class, () -> f.transaction.commit(candidate));
            String id = candidate.catalog().sources().keySet().iterator().next();
            Path path = f.store.root().resolve("rules/sources/" + id + ".java"); Files.write(path, new byte[]{1, 2});
            byte[] before = Files.readAllBytes(f.config);
            assertEquals(RuleMigrationTransaction.RecoveryState.BLOCKED, f.transaction.recover().state());
            assertArrayEquals(before, Files.readAllBytes(f.config)); assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(path));
        }
    }

    @Test public void plainResourceListsDoNotCreateScriptsOrBackups() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
            Map<String,Object> values = new LinkedHashMap<>();
            for (String path : RuleMigrationTransaction.LISTS) values.put(path, Arrays.asList("minecraft:arrow", "*:item"));
            f.holder.setAll(values, ConfigEditOrigin.API);
            byte[] config = Files.readAllBytes(f.config);
            RuleMigrationTransaction.PreparedMigration candidate = f.prepare();
            assertTrue(candidate.compiled().scripts().isEmpty());
            assertEquals(ConfigCommitResult.UNCHANGED, f.transaction.commit(candidate));
            assertFalse(Files.exists(f.store.root()));
            assertArrayEquals(config, Files.readAllBytes(f.config));
        }
    }

    @Test public void recoveryDistinguishesInterruptedInstallationFromFilesMissingAfterManifestSwitch() throws Exception {
        for (RuleMigrationTransaction.Checkpoint point : Arrays.asList(RuleMigrationTransaction.Checkpoint.AFTER_PREPARED, RuleMigrationTransaction.Checkpoint.AFTER_INSTALL)) {
            try (Fixture f = new Fixture(temporary.newFolder().toPath())) {
                f.transaction = f.transaction(at -> { if (at == point) throw new IllegalStateException("interrupted"); });
                RuleMigrationTransaction.PreparedMigration prepared = f.prepare();
                assertThrows(Exception.class, () -> f.transaction.commit(prepared));
                String id = prepared.catalog().sources().keySet().iterator().next();
                String path = "rules/sources/" + id + ".java";
                if (point == RuleMigrationTransaction.Checkpoint.AFTER_PREPARED) {
                    // A subset was installed but the manifest still describes the original catalog.
                    f.store.writeNew(path, prepared.catalog().files().get(path));
                    assertEquals(RuleMigrationTransaction.RecoveryState.READY, f.transaction.recover().state());
                    f.transaction = f.transaction(at -> {}); assertEquals(ConfigCommitResult.APPLIED, f.transaction.commit(f.prepare()));
                } else {
                    Files.delete(f.store.root().resolve(path));
                    assertEquals(RuleMigrationTransaction.RecoveryState.BLOCKED, f.transaction.recover().state());
                    assertFalse(Files.exists(f.store.root().resolve(path)));
                    assertEquals(Arrays.asList("minecraft:arrow", EXPRESSION, "minecraft:*"), f.holder.get(RuleMigrationTransaction.LISTS.get(0)));
                }
            }
        }
    }

    static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
    static final class Fixture implements AutoCloseable {
        final Path config; final RuleFileStore store; final ConfigHolder holder; final AotakeRuleConfigFixture configFixture;
        final ScriptSession<EntityRule> session;
        RuleMigrationTransaction transaction;
        Fixture(Path directory) throws Exception {
            config = directory.resolve("aotake-common.toml"); store = new RuleFileStore(directory.resolve("owner"));
            configFixture = new AotakeRuleConfigFixture(config); holder = configFixture.holder;
            session = BaniraScripts.open("aotake_sweep", EntityRule.class, "1", ScriptLimits.defaults(), Runnable::run);
            transaction = transaction(point -> {});
        }
        RuleMigrationTransaction transaction(java.util.function.Consumer<RuleMigrationTransaction.Checkpoint> fault) {
            return new RuleMigrationTransaction(store, holder, session, plan -> {}, fault);
        }
        ConfigEditSnapshot snapshot() { return holder.snapshotForEdit(RuleMigrationTransaction.PATHS); }
        RuleMigrationTransaction.PreparedMigration prepare() throws Exception { return transaction.prepare(snapshot()).get(15, TimeUnit.SECONDS); }
        @Override public void close() { session.close(); configFixture.close(); }
    }
}
