package xin.vanilla.aotake.rule;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.aotake.sweep.*;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SweepTaskLeaseTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    public static Consumer<SweepOutcome> onFinish = ignored -> {};
    private RuleManagerTest.Fixture fixture() throws Exception {
        RuleManagerTest.Fixture f = new RuleManagerTest.Fixture(temporary.newFolder().toPath(), true);
        f.await(f.manager.reload());
        String java = "package xin.vanilla.banira.generated.policies; public class CustomSweepPolicy extends xin.vanilla.aotake.api.sweep.DefaultSweepPolicy<Object> {"
                + " @Override public Object createState(xin.vanilla.aotake.api.sweep.SweepContext c){return \"task-state\";}"
                + " @Override public void onFinish(xin.vanilla.aotake.api.sweep.SweepContext c, Object state, xin.vanilla.aotake.api.sweep.SweepOutcome result){"
                + "if (!\"task-state\".equals(state)) throw new AssertionError(\"state was not created\");"
                + "xin.vanilla.aotake.rule.SweepTaskLeaseTest.onFinish.accept(result);} }";
        Files.write(f.store.path(SweepPolicyFileStoreTest.SOURCE), java.getBytes(StandardCharsets.UTF_8));
        f.holder.set(SweepPolicyCatalog.CONFIG_PATH, "custom"); f.await(f.manager.reload());
        try (RuleGeneration<Map<String, Object>>.PolicyLease lease = f.manager.leasePolicy(reason -> {})) {
            assertEquals("custom", lease.policyId());
            Class<?> type = lease.policy().getClass();
            assertEquals(Arrays.toString(type.getDeclaredMethods()), type,
                    type.getMethod("onFinish", SweepContext.class, Object.class, SweepOutcome.class).getDeclaringClass());
        }
        return f;
    }
    @After public void reset() { onFinish = ignored -> {}; }
    private SweepRequest request() { return SweepRequest.builder(SweepTrigger.CHUNK, SweepOperation.RECYCLE).build(); }

    @Test public void reloadCancelsReadyTaskAndReleasesLeaseOnlyAfterFinalization() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            AtomicReference<Backend> backend = new AtomicReference<>();
            SweepTask<?> task = SweepTaskFactory.create(UUID.randomUUID(), request(), f.manager,
                    lease -> { Backend b = new Backend(f.manager, lease); backend.set(b); return b; });
            assertNotNull(task.prepare()); assertEquals(SweepPhase.READY, task.phase());
            Backend b = backend.get(); onFinish = result -> {
                b.events.add("finish"); assertEquals(SweepPhase.CANCELLED, result.status());
                assertNotNull(b.lease.policy()); assertNotNull(b.lease.configuration());
            };
            f.await(f.manager.reload());
            assertEquals(SweepPhase.CANCELLED, task.phase());
            assertEquals(Arrays.asList("finish", "flush", "release"), b.events);
            assertThrows(IllegalStateException.class, b.lease::policy);
            assertEquals(0, b.executed);
        }
    }

    @Test public void finishAndPersistenceErrorsStillCloseLeaseAndKeepOriginalFailure() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            AtomicReference<Backend> backend = new AtomicReference<>();
            SweepTask<?> task = SweepTaskFactory.create(UUID.randomUUID(), request(), f.manager,
                    lease -> { Backend b = new Backend(f.manager, lease); b.failFlush = true; backend.set(b); return b; });
            onFinish = result -> { backend.get().events.add("finish"); throw new IllegalArgumentException("finish failure"); };
            PreparedSweep prepared = task.prepare(); prepared.start(); task.runNextBatch();
            assertEquals(SweepPhase.FAILED, task.phase()); assertEquals("finish failure", task.outcome().failure());
            assertEquals(Arrays.asList("finish", "flush", "release"), backend.get().events);
            assertThrows(IllegalStateException.class, backend.get().lease::configuration);
        }
    }

    @Test public void failedBackendConstructionAndMismatchedGenerationReleaseLease() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            AtomicReference<RuleGeneration<Map<String,Object>>.PolicyLease> held = new AtomicReference<>();
            assertThrows(IllegalArgumentException.class, () -> SweepTaskFactory.create(UUID.randomUUID(), request(), f.manager,
                    lease -> { held.set(lease); throw new IllegalArgumentException("backend construction"); }));
            assertThrows(IllegalStateException.class, () -> held.get().policy());
            assertThrows(IllegalStateException.class, () -> SweepTaskFactory.create(UUID.randomUUID(), request(), f.manager,
                    lease -> { held.set(lease); Backend b = new Backend(f.manager, lease); b.wrongGeneration = true; return b; }));
            assertThrows(IllegalStateException.class, () -> held.get().policy());
        }
    }

    @Test public void scanCompletionUsesSameTaskWithoutCleanupAndCannotBeConsumedAgain() throws Exception {
        try (RuleManagerTest.Fixture f = fixture()) {
            AtomicReference<Backend> backend = new AtomicReference<>();
            SweepTask<?> task = SweepTaskFactory.create(UUID.randomUUID(), request(), f.manager,
                    lease -> { Backend b = new Backend(f.manager, lease); backend.set(b); return b; });
            PreparedSweep prepared = task.prepare();
            prepared.completeScan("below chunk threshold");
            assertEquals(SweepPhase.SUCCESS, task.phase()); assertEquals("below chunk threshold", task.outcome().reason());
            assertEquals(0, backend.get().executed);
            assertThrows(IllegalStateException.class, prepared::start);
            assertThrows(IllegalStateException.class, () -> prepared.completeScan("again"));
        }
    }

    static final class Backend implements SweepBackend {
        final RuleManager<Map<String,Object>> manager;
        final RuleGeneration<Map<String,Object>>.PolicyLease lease;
        final List<String> events = new ArrayList<>(); boolean failFlush, wrongGeneration; int executed;
        Backend(RuleManager<Map<String,Object>> manager, RuleGeneration<Map<String,Object>>.PolicyLease lease) { this.manager=manager; this.lease=lease; }
        public long generationId(){ return lease.generationId() + (wrongGeneration ? 1 : 0); }
        public void requireUsable(){ if(!manager.isUsable(generationId())) throw new IllegalStateException("retired"); }
        public ServerAccess server(Runnable guard){ return null; }
        public NativeMembers members(){ return lease.members(); }
        public ConfigEditSnapshot configuration(){ return lease.configuration(); }
        public List<EntityAccess> collect(SweepRequest r,SweepScope s,Runnable g){ return Collections.emptyList(); }
        public EntityAccess normalize(EntityAccess e,SweepScope s,Runnable g){ return e; }
        public Object identity(EntityAccess e){ return e; }
        public DefaultSweepFilter.Entry<EntityAccess,Object> evaluate(SweepRequest r,EntityAccess e){ throw new AssertionError(); }
        public List<EntityAccess> filter(SweepRequest r,List<DefaultSweepFilter.Entry<EntityAccess,Object>> e){ return Collections.emptyList(); }
        public List<EntityAccess> preflight(SweepRequest r,SweepScope s,List<EntityAccess> e){ return e; }
        public void execute(BatchContext c,Progress p){ executed++; }
        public void flush(){ events.add("flush"); assertNotNull(lease.configuration()); if(failFlush) throw new IllegalArgumentException("flush failure"); }
        public void release(){ events.add("release"); assertNotNull(lease.policy()); }
        public void fault(Throwable error){ }
    }
}
