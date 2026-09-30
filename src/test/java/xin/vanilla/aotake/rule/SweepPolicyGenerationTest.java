package xin.vanilla.aotake.rule;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.api.sweep.SweepPolicy;
import xin.vanilla.banira.common.config.ConfigEditOrigin;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class SweepPolicyGenerationTest {
    public static Runnable constructorHook = () -> {};
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private RuleManagerTest.Fixture fixture() throws Exception { return new RuleManagerTest.Fixture(temporary.newFolder().toPath(), true); }
    private void source(RuleManagerTest.Fixture f, String text) throws Exception { Files.write(f.store.path(SweepPolicyFileStoreTest.SOURCE), text.getBytes(StandardCharsets.UTF_8)); }
    private String source(RuleManagerTest.Fixture f) throws Exception { return RuleFileStore.utf8(f.store.read(SweepPolicyFileStoreTest.SOURCE, RuleFileStore.MAX_SOURCE_BYTES)); }

    @Test public void compilesWholeGenerationAndCreatesIndependentTaskInstances() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload());
            assertEquals(new LinkedHashSet<>(Arrays.asList("builtin:default", "custom")), f.manager.policyIds());
            f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "custom");
            long id = f.await(f.manager.reload());
            try (RuleGeneration<Map<String, Object>>.PolicyLease a = f.manager.leasePolicy(reason -> {});
                 RuleGeneration<Map<String, Object>>.PolicyLease b = f.manager.leasePolicy(reason -> {})) {
                assertEquals(id, a.generationId()); assertEquals("custom", a.policyId());
                assertNotSame(a.policy(), b.policy()); assertSame(a.policy(), a.policy());
                assertEquals("custom", a.configuration().getValues().get(SweepPolicyCatalog.CONFIG_PATH));
            }
            // The execution generation is the only activation point, not either compiler session.
            assertTrue(f.session.active().isEmpty());
        }
    }

    @Test public void brokenPolicyKeepsHealthySameSelectionButChangedSelectionBlocksOldGeneration() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload()); f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "custom");
            long id = f.await(f.manager.reload()); String original = source(f);
            source(f, original + " invalid Java");
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertTrue(f.manager.isUsable(id));
            f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "builtin:default");
            assertFalse(f.manager.isUsable(id));
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertThrows(RuleExecutionException.class, () -> f.manager.acquire(RuleManager.RuleUse.SWEEP));
            source(f, original); assertNotEquals(id, (long) f.await(f.manager.reload()));
        }
    }

    @Test public void invalidRuleCannotPublishPreparedPolicyAndDoesNotChangeApprovedSelection() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            long id = f.await(f.manager.reload());
            String original = f.source(); f.replaceSource(original + " bad");
            source(f, source(f).replace("extends DefaultSweepPolicy<Void>", "extends DefaultSweepPolicy<String>"));
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertTrue(f.manager.isUsable(id));
            try (RuleGeneration<Map<String, Object>>.PolicyLease lease = f.manager.leasePolicy(reason -> {})) { assertEquals("builtin:default", lease.policyId()); }
            assertTrue(f.session.active().isEmpty());
        }
    }

    @Test public void policySelectionChangedDuringPreparationRejectsStaleCandidate() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload());
            CompletableFuture<Long> pending = f.manager.reload();
            f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "custom");
            assertThrows(ExecutionException.class, () -> f.await(pending));
            f.await(f.manager.reload());
            try (RuleGeneration<Map<String, Object>>.PolicyLease lease = f.manager.leasePolicy(reason -> {})) { assertEquals("custom", lease.policyId()); }
        }
    }

    @Test public void retiredGenerationRetainsResourcesUntilLastTaskRelease() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            long oldId = f.await(f.manager.reload());
            List<RuleManagerTest.Context> oldContexts = new ArrayList<>(f.contexts);
            int clears = oldContexts.stream().mapToInt(context -> context.clearCalls).sum();
            List<String> reasons = new ArrayList<>();
            RuleGeneration<Map<String, Object>>.PolicyLease first = f.manager.leasePolicy(reasons::add);
            RuleGeneration<Map<String, Object>>.PolicyLease second = f.manager.leasePolicy(reasons::add);
            SweepPolicy<?> instance = first.policy();
            f.await(f.manager.reload()); assertEquals(2, reasons.size()); assertFalse(f.manager.isUsable(oldId));
            assertEquals(clears, oldContexts.stream().mapToInt(context -> context.clearCalls).sum());
            assertSame(instance, first.policy()); first.close(); first.close();
            assertEquals(clears, oldContexts.stream().mapToInt(context -> context.clearCalls).sum());
            second.close(); assertTrue(oldContexts.stream().mapToInt(context -> context.clearCalls).sum() > clears);
            assertThrows(IllegalStateException.class, first::policy);
        }
    }

    @Test public void cancellationCallbackFailureCannotPreventOtherTasksOrRelease() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload()); List<RuleManagerTest.Context> oldContexts = new ArrayList<>(f.contexts);
            int clears = oldContexts.stream().mapToInt(context -> context.clearCalls).sum();
            f.manager.leasePolicy(reason -> { throw new IllegalStateException("cancel callback failed"); });
            RuleGeneration<Map<String, Object>>.PolicyLease[] last = new RuleGeneration.PolicyLease[1];
            last[0] = f.manager.leasePolicy(reason -> last[0].close());
            f.await(f.manager.reload()); assertTrue(oldContexts.stream().mapToInt(context -> context.clearCalls).sum() > clears);
            assertThrows(IllegalStateException.class, last[0]::policy);
        }
    }

    @Test public void remoteEditsOnlySelectPreparedIdsAndRejectWholeBatchWithoutReadingDisk() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload()); Files.delete(f.store.path(SweepPolicyFileStoreTest.SOURCE));
            Map<String, Object> edit = new LinkedHashMap<>(); edit.put("unrelated", 7);
            for (String invalid : Arrays.asList("missing", "../CustomSweepPolicy.java", "xin.vanilla.banira.generated.policies.CustomSweepPolicy", "public class Evil {}")) {
                edit.put(SweepPolicyCatalog.CONFIG_PATH, invalid);
                assertThrows(IllegalArgumentException.class, () -> f.holder.setAll(edit, ConfigEditOrigin.REMOTE));
                assertEquals(1, (int) f.holder.get("unrelated")); assertEquals("builtin:default", f.holder.get(SweepPolicyCatalog.CONFIG_PATH));
            }
            edit.put(SweepPolicyCatalog.CONFIG_PATH, "custom"); f.holder.setAll(edit, ConfigEditOrigin.REMOTE);
            assertEquals("custom", f.holder.get(SweepPolicyCatalog.CONFIG_PATH)); assertEquals(7, (int) f.holder.get("unrelated"));
        }
    }

    @Test public void policyEditedBySaveListenerCannotBePublishedOrOverwritten() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            String changed = "// edited while committing";
            Runnable unregister = f.holder.onSaved(paths -> {
                try { source(f, changed); } catch (Exception e) { throw new IllegalStateException(e); }
            });
            try { assertThrows(ExecutionException.class, () -> f.await(f.manager.reload())); }
            finally { unregister.run(); }
            assertEquals(changed, source(f));
            assertThrows(RuleExecutionException.class, () -> f.manager.acquire(RuleManager.RuleUse.SWEEP));
        }
    }

    @Test public void constructorCannotReturnAnInstanceAfterReentrantRetirement() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload());
            source(f, "package xin.vanilla.banira.generated.policies; public class CustomSweepPolicy extends xin.vanilla.aotake.api.sweep.DefaultSweepPolicy<Void> { public CustomSweepPolicy(){xin.vanilla.aotake.rule.SweepPolicyGenerationTest.constructorHook.run();} }");
            f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "custom"); long id = f.await(f.manager.reload());
            RuleGeneration<Map<String, Object>>.PolicyLease lease = f.manager.leasePolicy(reason -> {});
            constructorHook = () -> {
                f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "builtin:default");
                assertFalse(f.manager.isUsable(id));
            };
            assertThrows(RuntimeException.class, lease::policy);
            assertThrows(IllegalStateException.class, lease::policy);
        } finally { constructorHook = () -> {}; }
    }

    @Test public void retirementCallbackSourceChangesPreventPublication() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload());
            try (RuleGeneration<Map<String, Object>>.PolicyLease lease = f.manager.leasePolicy(reason -> {
                try { source(f, "// changed during retirement"); } catch (Exception e) { throw new IllegalStateException(e); }
            })) {
                assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
                assertThrows(RuleExecutionException.class, () -> f.manager.acquire(RuleManager.RuleUse.SWEEP));
                assertEquals("// changed during retirement", source(f));
            }
        }
    }

    @Test public void invalidNativeDeclarationDoesNotReplaceHealthyGeneration() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            long id = f.await(f.manager.reload());
            com.google.gson.JsonObject manifest = RuleFileStore.object(f.store.read(SweepPolicyFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
            com.google.gson.JsonObject member = new com.google.gson.JsonObject();
            member.addProperty("id", "missing"); member.addProperty("kind", "METHOD"); member.addProperty("owner", "java.lang.System");
            member.addProperty("name", "currentTimeMillis"); member.addProperty("descriptor", "()J"); member.addProperty("functionType", "missing.NativeFunction");
            manifest.getAsJsonArray("policies").get(0).getAsJsonObject().getAsJsonArray("nativeMembers").add(member);
            Files.write(f.store.path(SweepPolicyFileStore.MANIFEST), RuleFileStore.jsonBytes(manifest));
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertTrue(f.manager.isUsable(id));
        }
    }

    @Test public void entityRuleMembersAreBoundBeforeActivationAndReusedDuringEvaluation() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            f.await(f.manager.reload());
            f.replaceSource(f.source().replaceAll("return .*;", "return ((java.util.function.LongSupplier) context.members().require(\"clock\", java.util.function.LongSupplier.class)).getAsLong() > 0;"));
            com.google.gson.JsonObject manifest = RuleFileStore.object(f.store.read(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES));
            com.google.gson.JsonObject member = new com.google.gson.JsonObject();
            member.addProperty("id", "clock"); member.addProperty("kind", "METHOD"); member.addProperty("owner", "java.lang.System");
            member.addProperty("name", "currentTimeMillis"); member.addProperty("descriptor", "()J"); member.addProperty("functionType", "java.util.function.LongSupplier");
            com.google.gson.JsonArray members = new com.google.gson.JsonArray(); members.add(member);
            String selected = ((List<String>) f.holder.get(RuleMigrationTransaction.LISTS.get(0))).get(0).substring(7);
            manifest.getAsJsonObject("rules").getAsJsonObject(selected).add("nativeMembers", members);
            Files.write(f.store.path(RuleFileStore.MANIFEST), RuleFileStore.jsonBytes(manifest));
            long id = f.await(f.manager.reload());
            for (int i = 0; i < 100; i++) assertTrue(f.manager.acquire(RuleManager.RuleUse.SWEEP).matches(RuleGeneration.RuleList.SWEEP, RuleManagerTest.entity("test:item", 0)));
            member.addProperty("descriptor", "()Z");
            Files.write(f.store.path(RuleFileStore.MANIFEST), RuleFileStore.jsonBytes(manifest));
            assertThrows(ExecutionException.class, () -> f.await(f.manager.reload()));
            assertTrue(f.manager.isUsable(id));
        }
    }
}
