package xin.vanilla.aotake.rule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.common.config.*;
import xin.vanilla.banira.internal.forge.config.AotakeRuleConfigFixture;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class RuleManagerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void startupBlocksUntilMigrationCommitThenUsesAllFourListsAndCapturedModes() throws Exception {
        try (Fixture f = fixture()) {
            for (RuleManager.RuleUse use : RuleManager.RuleUse.values())
                assertThrows(RuleExecutionException.class, () -> f.manager.acquire(use));
            CompletableFuture<Long> reload = f.manager.reload();
            assertThrows(RuleExecutionException.class, () -> f.manager.acquire(RuleManager.RuleUse.SWEEP));
            long id = f.await(reload);
            RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            assertEquals(id, generation.id());
            for (RuleGeneration.RuleList list : RuleGeneration.RuleList.values()) {
                assertTrue(generation.matches(list, entity("test:item", 6)));
                assertFalse(generation.matches(list, entity("test:item", 1)));
            }
            assertFalse(generation.shouldClean(false, entity("test:item", 6)));
            assertTrue(generation.shouldClean(true, entity("test:item", 1)));
            assertTrue(Files.exists(f.store.root().resolve("rules/manifest.json")));
            assertTrue(((List<?>) f.holder.get(RuleMigrationTransaction.LISTS.get(0))).get(0).toString().startsWith("script:"));
        }
    }

    @Test public void sourceFailureKeepsSameSelectionButChangedModeCannotUseOldGeneration() throws Exception {
        try (Fixture f = fixture()) {
            long id = f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> old = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            f.replaceSource("this is not Java");
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertTrue(f.manager.isUsable(id));
            assertSame(old, f.manager.acquire(RuleManager.RuleUse.SWEEP));
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            // Even an in-memory edit that has not emitted a save event cannot mix modes.
            assertThrows(RuleExecutionException.class, () -> f.manager.acquire(RuleManager.RuleUse.SWEEP));
            assertFalse(f.manager.isUsable(id));
            assertThrows(RuleExecutionException.class, () -> old.shouldClean(false, entity("test:item", 1)));
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
        }
    }

    @Test public void delayedBatchGateDetectsUnsavedModeChangesWithoutAcquiringAnotherScan() throws Exception {
        try (Fixture f = fixture()) {
            long id = f.await(f.manager.reload());
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            assertFalse(f.manager.isUsable(id));
        }
    }

    @Test public void runtimeFailurePoisonsWholeGenerationAndNeverBecomesWhiteListNonMatch() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            f.replaceSource(f.source().replace("return ", "if (context.tick() < 0) throw new IllegalStateException(\"broken\"); return "));
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            assertThrows(RuleExecutionException.class, () -> generation.shouldClean(false, entity("test:item", -1)));
            assertFalse(f.manager.isUsable(generation.id()));
            for (RuleManager.RuleUse use : RuleManager.RuleUse.values())
                assertThrows(RuleExecutionException.class, () -> f.manager.acquire(use));
            assertThrows(RuleExecutionException.class, () -> generation.matches(RuleGeneration.RuleList.REDLIST, entity("test:item", 8)));
            assertTrue(f.contexts.stream().allMatch(Context::released));
        }
    }

    @Test public void failuresInRedlistOrCaptureAlsoBlockFurtherSweepAndChunkWork() throws Exception {
        for (RuleGeneration.RuleList list : Arrays.asList(RuleGeneration.RuleList.REDLIST, RuleGeneration.RuleList.CAPTURE)) {
            try (Fixture f = fixture()) {
                f.await(f.manager.reload());
                f.replaceSource(f.source().replace("return ", "if (context.tick() < 0) throw new AssertionError(\"broken\"); return "));
                f.await(f.manager.reload());
                RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.RECYCLE);
                assertThrows(RuleExecutionException.class, () -> generation.matches(list, entity("test:item", -1)));
                assertFalse(f.manager.isUsable(generation.id()));
                assertThrows(RuleExecutionException.class, () -> generation.shouldClean(true, entity("test:item", 8)));
                assertTrue(f.contexts.stream().allMatch(Context::released));
            }
        }
    }

    @Test public void replacingGenerationInvalidatesRetainedScanAndDoesNotRetainEntity() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> old = f.manager.acquire(RuleManager.RuleUse.CHUNK);
            assertTrue(old.matches(RuleGeneration.RuleList.CHUNK, entity("test:item", 8)));
            String previous = f.source();
            String edited = previous.replaceAll("return .*;", "return context.tick() >= 10;");
            assertNotEquals(previous, edited);
            f.replaceSource(edited);
            long replacement = f.await(f.manager.reload());
            assertNotEquals(old.id(), replacement);
            assertThrows(RuleExecutionException.class, () -> old.matches(RuleGeneration.RuleList.CHUNK, entity("test:item", 8)));
            assertFalse(f.manager.acquire(RuleManager.RuleUse.CHUNK).matches(RuleGeneration.RuleList.CHUNK, entity("test:item", 8)));
            assertTrue(f.contexts.stream().allMatch(Context::released));
        }
    }

    @Test public void guardRejectsRemoteDslAndUnknownIdsWithoutFileAccessOrPartialMutation() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            Path source = f.sourcePath();
            Files.delete(source);
            Map<String,Object> change = new LinkedHashMap<>();
            change.put("unrelated", 9);
            change.put(RuleMigrationTransaction.LISTS.get(0), Arrays.asList("minecraft:arrow", "tick -> tick > 7"));
            assertThrows(IllegalArgumentException.class, () -> f.holder.setAll(change, ConfigEditOrigin.REMOTE));
            assertEquals(1, (int) f.holder.get("unrelated"));
            change.put(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("script:unknown"));
            assertThrows(IllegalArgumentException.class, () -> f.holder.setAll(change, ConfigEditOrigin.REMOTE));
            change.put(RuleMigrationTransaction.LISTS.get(0), f.holder.get(RuleMigrationTransaction.LISTS.get(1)));
            f.holder.setAll(change, ConfigEditOrigin.REMOTE);
            assertEquals(9, (int) f.holder.get("unrelated"));
            assertFalse(Files.exists(source));
        }
    }

    @Test public void closeCancelsLateCompilationInvalidatesScansAndUnregistersGuard() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> old = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            CompletableFuture<Long> pending = f.manager.reload();
            f.manager.close();
            assertThrows(CancellationException.class, pending::join);
            f.drain();
            assertFalse(f.manager.isUsable(old.id()));
            assertThrows(RuleExecutionException.class, () -> old.matches(RuleGeneration.RuleList.CAPTURE, entity("test:item", 8)));
            assertTrue(f.session.active().isEmpty());
            f.holder.setAll(Collections.singletonMap(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("tick -> tick > 9")), ConfigEditOrigin.API);
            assertTrue(f.contexts.stream().allMatch(Context::released));
        }
    }

    @Test public void resourceWildcardsAndEmptyRulesKeepExistingBlackWhiteSemantics() throws Exception {
        try (Fixture f = fixture(false)) {
            f.holder.set(RuleMigrationTransaction.LISTS.get(0), Arrays.asList("test:*", "*:arrow"));
            f.holder.set(RuleMigrationTransaction.LISTS.get(1), Collections.emptyList());
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            f.holder.save(); f.start(); f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            assertTrue(generation.shouldClean(false, entity("test:stone", 0)));
            assertTrue(generation.shouldClean(false, entity("other:arrow", 0)));
            assertFalse(generation.shouldClean(false, entity("other:stone", 0)));
            assertTrue(generation.shouldClean(true, entity("other:stone", 0)));
            f.holder.set(RuleMigrationTransaction.MODES.get(1), "BLACK");
            f.await(f.manager.reload());
            assertFalse(f.manager.acquire(RuleManager.RuleUse.CHUNK).shouldClean(true, entity("other:stone", 0)));
        }
    }

    @Test public void coalescedReloadHasOnePublicationAndOnlyOwnerMayReadEntities() throws Exception {
        try (Fixture f = fixture()) {
            CompletableFuture<Long> first = f.manager.reload();
            assertSame(first, f.manager.reload());
            assertSame(first, f.manager.reload());
            long id = f.await(first);
            RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            assertEquals(id, generation.id());
            Throwable error = CompletableFuture.supplyAsync(() -> {
                try { generation.matches(RuleGeneration.RuleList.SWEEP, entity("test:item", 8)); return null; }
                catch (Throwable ex) { return ex; }
            }).get(5, TimeUnit.SECONDS);
            assertTrue(error instanceof IllegalStateException);
            assertTrue(f.manager.isUsable(id));
        }
    }

    @Test public void reloadBetweenOwnerCallbacksCannotPublishAnExpiredCandidateOrLoseTheLatestRequest() throws Exception {
        try (Fixture f = fixture()) {
            CompletableFuture<Long> first = f.manager.reload();
            // Run instantiation and coordinator delivery individually, just as separate server tasks.
            f.nextOwnerTask();
            f.nextOwnerTask();
            CompletableFuture<Long> latest = f.manager.reload();
            f.await(first);
            assertEquals((long) f.await(latest), f.manager.acquire(RuleManager.RuleUse.SWEEP).id());
        }
    }

    @Test public void changedConfigDuringPreparationCannotPublishOrOverwriteExternalEdit() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> old = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            CompletableFuture<Long> pending = f.manager.reload();
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            assertThrows(ExecutionException.class, () -> f.await(pending));
            assertEquals("BLACK", f.holder.get(RuleMigrationTransaction.MODES.get(0)));
            assertFalse(f.manager.isUsable(old.id()));
            assertThrows(RuleExecutionException.class, () -> old.shouldClean(false, entity("test:item", 8)));
            f.await(f.manager.reload());
            assertTrue(f.manager.acquire(RuleManager.RuleUse.SWEEP).shouldClean(false, entity("test:item", 8)));
        }
    }

    @Test public void savedRuleChangesReloadButUnrelatedSettingsDoNotReplaceGeneration() throws Exception {
        try (Fixture f = fixture()) {
            long id = f.await(f.manager.reload());
            f.holder.set("unrelated", 2); f.holder.save(); f.drain();
            assertTrue(f.manager.isUsable(id));
            assertTrue(f.owner.isEmpty());
            f.holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("minecraft:arrow"));
            f.holder.save();
            f.nextOwnerTask();
            assertFalse(f.manager.isUsable(id));
            long replacement = f.await(f.manager.reload());
            assertNotEquals(id, replacement);
            assertTrue(f.manager.acquire(RuleManager.RuleUse.SWEEP).shouldClean(false, entity("test:item", 8)));
        }
    }

    @Test public void reloadFromMigrationSaveListenerIsNotLostOrPublishedPrematurely() throws Exception {
        try (Fixture f = fixture(false)) {
            Queue<Runnable> workers = new ArrayDeque<>();
            f.start(workers::add);
            boolean[] once = {false};
            Runnable remove = f.holder.onSaved(paths -> {
                if (!once[0]) {
                    once[0] = true;
                    f.manager.reload();
                }
            });
            try {
                CompletableFuture<Long> reload = f.manager.reload();
                workers.remove().run();
                f.nextOwnerTask(); f.nextOwnerTask();
                assertTrue("Save listener was not exercised", once[0]);
                assertFalse("Older candidate was published despite a newer request", reload.isDone());
                assertEquals(1, workers.size());
                workers.remove().run();
                assertEquals((long) f.await(reload), f.manager.acquire(RuleManager.RuleUse.SWEEP).id());
            } finally { remove.run(); }
        }
    }

    @Test public void rejectedOwnerDispatchDoesNotPoisonSubsequentReloads() throws Exception {
        try (Fixture f = fixture()) {
            f.await(f.manager.reload());
            String original = f.source();
            f.replaceSource("broken Java");
            f.rejectOwner = true;
            CompletableFuture<Long> failed = f.manager.reload();
            assertThrows(ExecutionException.class, () -> f.await(failed));
            f.rejectOwner = false;
            f.replaceSource(original);
            CompletableFuture<Long> recovered = f.manager.reload();
            assertNotSame(failed, recovered);
            assertEquals((long) f.await(recovered), f.manager.acquire(RuleManager.RuleUse.SWEEP).id());
        }
    }

    @Test public void fourListsHaveIndependentResultsAndModes() throws Exception {
        try (Fixture f = fixture(false)) {
            for (int i = 0; i < RuleMigrationTransaction.LISTS.size(); i++)
                f.holder.set(RuleMigrationTransaction.LISTS.get(i), Collections.singletonList("tick -> tick == " + i));
            f.holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
            f.holder.save(); f.start(); f.await(f.manager.reload());
            RuleGeneration<Map<String,Object>> generation = f.manager.acquire(RuleManager.RuleUse.SWEEP);
            for (RuleGeneration.RuleList list : RuleGeneration.RuleList.values()) {
                for (int tick = 0; tick < 4; tick++)
                    assertEquals(list.name(), tick == list.ordinal(), generation.matches(list, entity("test:item", tick)));
            }
            assertTrue(generation.shouldClean(false, entity("test:item", 0)));
            assertFalse(generation.shouldClean(false, entity("test:item", 1)));
            assertTrue(generation.shouldClean(true, entity("test:item", 0)));
            assertFalse(generation.shouldClean(true, entity("test:item", 1)));
        }
    }

    @Test public void sourceEditAfterPreparationIsRejectedAndKeepsPreviouslyPublishedRules() throws Exception {
        try (Fixture f = fixture()) {
            long original = f.await(f.manager.reload());
            CompletableFuture<Long> reload = f.manager.reload();
            assertFalse("Candidate must still be awaiting owner publication", reload.isDone());
            f.replaceSource(f.source() + "\n// changed after preparation\n");
            assertThrows(ExecutionException.class, () -> f.await(reload));
            assertTrue(f.manager.isUsable(original));
        }
    }

    @Test public void stopFromSaveListenerNeverPublishesLateCandidate() throws Exception {
        try (Fixture f = fixture()) {
            Runnable remove = f.holder.onSaved(paths -> f.manager.close());
            try {
                CompletableFuture<Long> reload = f.manager.reload();
                assertThrows(CancellationException.class, () -> f.await(reload));
                assertTrue(f.session.active().isEmpty());
                assertTrue(f.contexts.stream().allMatch(Context::released));
            } finally { remove.run(); }
        }
    }

    private Fixture fixture() throws Exception { return fixture(true); }
    private Fixture fixture(boolean start) throws Exception { return new Fixture(temporary.newFolder().toPath(), start); }
    static Map<String,Object> entity(String resource, int tick) {
        Map<String,Object> value = new HashMap<>(); value.put("resource", resource); value.put("tick", tick); return value;
    }
    static final class Context extends LazyRuleContext<Map<String,Object>> {
        @Override protected Object read(RuleBindingPlan.Binding binding) { return subject().get(binding.payload()); }
        boolean released() { return subject() == null; }
    }
    static final class Fixture implements AutoCloseable {
        final Queue<Runnable> owner = new ConcurrentLinkedQueue<>();
        final List<Context> contexts = new ArrayList<>();
        final AotakeRuleConfigFixture config;
        final ConfigHolder holder;
        final RuleFileStore store;
        final ScriptSession<EntityRule> session;
        volatile boolean rejectOwner;
        RuleManager<Map<String,Object>> manager;
        Fixture(Path root, boolean start) throws Exception {
            config = new AotakeRuleConfigFixture(root.resolve("test.toml")); holder = config.holder;
            for (String path : RuleMigrationTransaction.LISTS) holder.set(path, Collections.singletonList("tick -> tick >= 5"));
            holder.save(); store = new RuleFileStore(root.resolve("owner"));
            session = BaniraScripts.open("aotake_sweep", EntityRule.class, "1", ScriptLimits.defaults(), owner::add);
            if (start) start();
        }
        void start() { start(Runnable::run); }
        void start(Executor worker) {
            manager = new RuleManager<>(store, holder, session, worker, task -> {
                if (rejectOwner) throw new RejectedExecutionException("Owner temporarily unavailable");
                owner.add(task);
            }, plan -> {
                Context context = new Context(); contexts.add(context); return context;
            });
        }
        <T> T await(CompletableFuture<T> future) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!future.isDone() && System.nanoTime() < deadline) { drain(); Thread.sleep(1); }
            return future.get(1, TimeUnit.SECONDS);
        }
        void drain() { Runnable task; while ((task = owner.poll()) != null) task.run(); }
        void nextOwnerTask() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            Runnable task;
            while ((task = owner.poll()) == null && System.nanoTime() < deadline) Thread.sleep(1);
            assertNotNull("Owner callback was not scheduled", task); task.run();
        }
        Path sourcePath() throws Exception {
            String reference = ((List<String>) holder.get(RuleMigrationTransaction.LISTS.get(0))).get(0);
            return store.root().resolve(RuleCatalog.sourcePath(reference.substring(7)));
        }
        String source() throws Exception { return new String(Files.readAllBytes(sourcePath()), StandardCharsets.UTF_8); }
        void replaceSource(String text) throws Exception { Files.write(sourcePath(), text.getBytes(StandardCharsets.UTF_8)); }
        @Override public void close() { if (manager != null) manager.close(); session.close(); drain(); config.close(); }
    }
}
