package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import java.util.function.Function;
import xin.vanilla.aotake.sweep.SweepExecutionPlan.Hook;

/** Owner-thread state machine. No production cleanup entry point is implicit in this class. */
public final class SweepTask<S> {
    private final UUID id;
    private final Thread owner = Thread.currentThread();
    private final long generationId;
    private SweepRequest request;
    private SweepBackend backend;
    private SweepPolicy<S> policy;
    private SweepExecutionPlan plan;
    private S state;
    private SweepScope scope;
    private Map<String, Object> configuration;
    private List<EntityAccess> selected = Collections.emptyList();
    private List<List<EntityAccess>> batches = Collections.emptyList();
    private SweepCandidates candidates;
    private SweepPhase phase = SweepPhase.CREATED;
    private SweepOutcome outcome;
    private Runnable completion = () -> {};
    private Runnable afterRelease;
    private final List<String> diagnostics = new ArrayList<>();
    private Hook hook;
    private long callback;
    private boolean closed, finishing, driving;
    private String cancellationReason;
    private int scanned, selectedCount, batchIndex, batchCount, offset;
    private int processed, skipped, failed, committed, pendingRemoval;
    private boolean pendingPersistence;

    public SweepTask(UUID id, SweepRequest request, SweepBackend backend, SweepPolicy<S> policy) {
        this(id, request, backend, policy, () -> {});
    }
    SweepTask(UUID id, SweepRequest request, SweepBackend backend, SweepPolicy<S> policy, Runnable afterRelease) {
        this(id, request, backend, policy, SweepExecutionPlan.resolve(policy.getClass()), afterRelease);
    }
    SweepTask(UUID id, SweepRequest request, SweepBackend backend, SweepPolicy<S> policy, SweepExecutionPlan plan, Runnable afterRelease) {
        this.id = Objects.requireNonNull(id, "id"); this.request = Objects.requireNonNull(request, "request");
        this.backend = Objects.requireNonNull(backend, "backend"); this.policy = Objects.requireNonNull(policy, "policy");
        this.plan = Objects.requireNonNull(plan, "execution plan"); plan.requireType(policy);
        candidates = new SweepCandidates(backend::identity);
        generationId = backend.generationId(); scope = request.scope();
        this.afterRelease = Objects.requireNonNull(afterRelease);
    }
    public UUID id() { return id; }
    public SweepPhase phase() { return phase; }
    public SweepOutcome outcome() { return outcome; }
    int selectedCount() { checkDriver(); return selectedCount; }
    void onCompletion(Runnable completion) { checkDriver(); this.completion = Objects.requireNonNull(completion); }
    void checkDriver() {
        checkOwner();
        if (hook != null || finishing || driving) throw new IllegalStateException("Recursive sweep execution");
    }
    private void checkOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Sweep requires owner thread"); }
    private void checkEntityView() {
        checkOwner();
        if (closed) throw new IllegalStateException("Sweep task has ended");
    }
    private void checkpoint() {
        if (cancellationReason != null) throw new Cancelled(cancellationReason);
        try { backend.requireUsable(); }
        catch (IllegalStateException retired) { throw new Cancelled("generation unavailable: " + retired.getMessage()); }
    }
    private <T> T invoke(Hook next, Function<Context, T> action) {
        if (hook != null) throw new IllegalStateException("Nested lifecycle hook");
        hook = next;
        Context context = new Context(++callback);
        try { return action.apply(context); }
        finally { hook = null; }
    }
    private <T> T step(Hook next, Function<Context, T> action) {
        checkpoint(); T result = invoke(next, action); checkpoint(); return result;
    }

    public PreparedSweep prepare() {
        checkDriver();
        if (phase != SweepPhase.CREATED) throw new IllegalStateException("Sweep already prepared");
        driving = true;
        try {
            checkpoint();
            configuration = Objects.requireNonNull(backend.configuration(), "configuration").getValues();
            state = plan.custom(Hook.STATE) ? invoke(Hook.STATE, c -> policy.createState(c)) : null;
            checkpoint();
            phase = SweepPhase.COLLECTING;
            if (plan.custom(Hook.BEFORE_COLLECT)) requireContinue(step(Hook.BEFORE_COLLECT, c -> policy.beforeCollect(c, state)), "beforeCollect");
            List<EntityAccess> collected = Objects.requireNonNull(plan.custom(Hook.COLLECT)
                    ? step(Hook.COLLECT, c -> policy.collectEntities(c, state)) : collectDefault(), "candidates");
            checkpoint();
            if (plan.custom(Hook.AFTER_COLLECT)) {
                List<EntityAccess> mutable = new ArrayList<>(collected);
                step(Hook.AFTER_COLLECT, c -> { policy.afterCollect(c, state, mutable); return null; });
                collected = mutable;
            }
            boolean canonical = request.candidates() == null && !plan.custom(Hook.COLLECT)
                    && !plan.custom(Hook.AFTER_COLLECT) && backend.collectedCandidatesAreCanonical();
            for (EntityAccess candidate : collected) if (candidate != null) {
                EntityAccess entity = canonical ? candidate : backend.normalize(candidate, scope, this::checkEntityView);
                if (entity != null) candidates.add(entity);
            }
            scanned = candidates.records().size();
            phase = SweepPhase.FILTERING;
            if (plan.custom(Hook.BEFORE_FILTER)) requireContinue(step(Hook.BEFORE_FILTER, c -> policy.beforeFilter(c, state, candidates.entities())), "beforeFilter");
            boolean builtin = !plan.custom(Hook.BEFORE_ENTITY) && !plan.custom(Hook.EVALUATE) && !plan.custom(Hook.AFTER_ENTITY);
            for (int i = 0; i < candidates.records().size(); i++) {
                final int index = i;
                SweepCandidates.Record record = candidates.records().get(i);
                EntityAccess entity = record.entity;
                if (builtin) {
                    captureDefault(record);
                    continue;
                }
                FlowControl control = plan.custom(Hook.BEFORE_ENTITY)
                        ? Objects.requireNonNull(step(Hook.BEFORE_ENTITY, c -> policy.beforeEntity(c.entity(entity, index), state)), "beforeEntity result") : FlowControl.CONTINUE;
                if (control == FlowControl.CANCEL) throw new Cancelled("beforeEntity");
                record.update(control == FlowControl.SKIP, control == FlowControl.SKIP ? EntityDisposition.KEEP
                        : plan.custom(Hook.EVALUATE) ? step(Hook.EVALUATE, c -> policy.evaluateEntity(c.entity(entity, index), state)) : EntityDisposition.DEFAULT);
                captureDefault(record);
                if (plan.custom(Hook.AFTER_ENTITY)) {
                    EntityEvaluation evaluated = record.evaluation();
                    EntityEvaluation adjusted = Objects.requireNonNull(step(Hook.AFTER_ENTITY,
                            c -> policy.afterEntity(c.entity(entity, index), state, evaluated)), "afterEntity result");
                    if (backend.identity(adjusted.entity()) != backend.identity(entity)) throw new IllegalArgumentException("afterEntity changed entity identity");
                    if (evaluated.skipped() && !adjusted.skipped()) throw new IllegalArgumentException("Skipped entity cannot be selected");
                    if (adjusted.cancelled()) throw new Cancelled("afterEntity");
                    record.update(adjusted.skipped(), adjusted.disposition());
                    captureDefault(record);
                }
            }
            List<EntityAccess> choice;
            if (plan.custom(Hook.FILTER)) {
                List<EntityEvaluation> completed = candidates.evaluations();
                choice = new ArrayList<>(Objects.requireNonNull(step(Hook.FILTER, c -> policy.finishFiltering(c, state, completed)), "selected"));
                candidates.restrict(choice);
            } else choice = candidates.filterDefault(request.ignoreFilter() || request.preFiltered(), entries -> backend.filter(request, entries));
            checkpoint();
            if (plan.custom(Hook.AFTER_FILTER)) step(Hook.AFTER_FILTER, c -> { policy.afterFilter(c, state, choice); return null; });
            selected = plan.custom(Hook.AFTER_FILTER) || plan.custom(Hook.FILTER) ? candidates.restrict(choice) : choice;
            selectedCount = selected.size();
            if (request.operation() == SweepOperation.SCAN_ONLY) { finish(SweepPhase.SUCCESS, "scan complete", null); return null; }
            phase = SweepPhase.READY;
            return new PreparedSweep(this, generationId);
        } catch (RuntimeException | Error error) { terminate(error); return null; }
        finally { driving = false; }
    }

    private void captureDefault(SweepCandidates.Record record) {
        if (record.skipped || record.disposition != EntityDisposition.DEFAULT || request.ignoreFilter() || request.preFiltered()) return;
        if (record.scan == null) {
            checkpoint();
            record.scan = Objects.requireNonNull(backend.evaluate(request, record.entity), "default evaluation");
            checkpoint();
        }
    }

    private List<EntityAccess> normalize(List<EntityAccess> candidates) {
        List<EntityAccess> result = new ArrayList<>(candidates.size());
        Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EntityAccess candidate : candidates) {
            if (candidate == null) continue;
            EntityAccess entity = backend.normalize(candidate, scope, this::checkEntityView);
            if (entity != null && identities.add(Objects.requireNonNull(backend.identity(entity), "identity"))) result.add(entity);
        }
        return result;
    }
    private List<EntityAccess> restrict(List<EntityAccess> choice, IdentityHashMap<Object, EntityAccess> allowed) {
        List<EntityAccess> result = new ArrayList<>(choice.size());
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EntityAccess e : choice) {
            Object identity = backend.identity(Objects.requireNonNull(e, "selected entity"));
            EntityAccess canonical = allowed.get(identity);
            if (canonical == null) throw new IllegalArgumentException("Selected entity was not evaluated or was skipped");
            if (seen.add(identity)) result.add(canonical);
        }
        return result;
    }

    void completeScan(String reason) {
        checkDriver();
        if (phase != SweepPhase.READY) throw new IllegalStateException("Sweep is not ready");
        Objects.requireNonNull(reason);
        driving = true;
        try { checkpoint(); finish(SweepPhase.SUCCESS, reason, null); }
        catch (RuntimeException | Error error) { terminate(error); }
        finally { driving = false; }
    }

    void start() {
        checkDriver();
        if (phase != SweepPhase.READY) throw new IllegalStateException("Sweep is not ready");
        driving = true;
        try {
            checkpoint();
            IdentityHashMap<Object, EntityAccess> allowed = new IdentityHashMap<>();
            for (EntityAccess e : selected) allowed.put(backend.identity(e), e);
            List<EntityAccess> ready = normalize(Objects.requireNonNull(backend.preflight(request, scope, Collections.unmodifiableList(selected)), "preflight"));
            selected = restrict(ready, allowed);
            skipped += selectedCount - selected.size();
            if (plan.custom(Hook.BEFORE_CLEANUP)) requireContinue(step(Hook.BEFORE_CLEANUP, c -> policy.beforeCleanup(c, state, Collections.unmodifiableList(selected))), "beforeCleanup");
            phase = SweepPhase.RUNNING;
            batches = selected.isEmpty() && request.operation() == SweepOperation.RECYCLE
                    ? Collections.singletonList(Collections.emptyList()) : backend.batches(request, Collections.unmodifiableList(selected));
            batchCount = batches.size();
            if (batchCount == 0) finish(SweepPhase.SUCCESS, "no candidates", null);
        } catch (RuntimeException | Error error) { terminate(error); }
        finally { driving = false; }
    }

    public void runNextBatch() {
        checkDriver();
        if (phase != SweepPhase.RUNNING) throw new IllegalStateException("Sweep is not running");
        driving = true;
        try {
            checkpoint();
            List<EntityAccess> entities = Collections.unmodifiableList(batches.get(batchIndex));
            int end = offset + entities.size();
            SweepBackend.Progress progress = new SweepBackend.Progress(entities.size());
            Throwable failure = null;
            boolean cancel = false;
            try {
                FlowControl control = plan.custom(Hook.BEFORE_BATCH)
                        ? Objects.requireNonNull(step(Hook.BEFORE_BATCH, c -> policy.beforeBatch(c.batch(entities), state)), "beforeBatch result") : FlowControl.CONTINUE;
                if (control == FlowControl.SKIP) progress.skipped(entities.size());
                else if (control == FlowControl.CANCEL) { cancel = true; cancellationReason = "beforeBatch"; }
                else {
                    // Execution is engine-owned, but its view uses the same callback lifetime fence.
                    invoke(Hook.BEFORE_BATCH, c -> { backend.execute(c.batch(entities), progress); return null; });
                    checkpoint();
                    if (progress.snapshot(false, null).remaining() != 0) throw new IllegalStateException("Batch left unaccounted entities");
                }
            } catch (Cancelled error) { cancel = true; cancellationReason = error.getMessage(); }
            catch (RuntimeException | Error error) { rethrowFatal(error); failure = error; }
            BatchResult result = progress.snapshot(cancel, failure == null ? null : message(failure));
            processed += result.processed(); skipped += result.skipped(); failed += result.failed();
            committed = Math.addExact(committed, result.committed()); pendingRemoval = Math.addExact(pendingRemoval, result.pendingRemoval());
            pendingPersistence |= result.pendingPersistence();
            try { if (plan.custom(Hook.AFTER_BATCH)) invoke(Hook.AFTER_BATCH, c -> { policy.afterBatch(c.batch(entities), state, result); return null; }); }
            catch (RuntimeException | Error error) { rethrowFatal(error); if (failure == null) failure = error; else diagnostics.add(message(error)); }
            offset = end; batchIndex++;
            if (failure != null) terminate(failure);
            else if (cancel || cancellationReason != null) finish(SweepPhase.CANCELLED, cancellationReason, null);
            else { checkpoint(); if (batchIndex == batchCount) finish(SweepPhase.SUCCESS, "completed", null); }
        } catch (RuntimeException | Error error) { terminate(error); }
        finally { driving = false; }
    }

    public void cancel(String reason) {
        checkOwner();
        if (closed || finishing) return;
        cancellationReason = Objects.requireNonNull(reason, "reason");
        if (hook == null && !driving) finish(SweepPhase.CANCELLED, reason, null);
    }
    private void requireContinue(StageControl control, String stage) {
        if (Objects.requireNonNull(control, stage) == StageControl.CANCEL) throw new Cancelled(stage);
    }
    private void terminate(Throwable failure) {
        rethrowFatal(failure);
        if (failure instanceof Cancelled) finish(SweepPhase.CANCELLED, message(failure), null);
        else finish(SweepPhase.FAILED, "execution failed", failure);
    }
    private void finish(SweepPhase status, String reason, Throwable failure) {
        if (closed || finishing) return;
        finishing = true;
        SweepPhase stoppedAt = phase;
        phase = status;
        if (status == SweepPhase.CANCELLED) cancellationReason = reason;
        try {
            if (failure != null) reportFault(failure);
            SweepOutcome beforeFinish = snapshot(stoppedAt, reason, failure);
            try { if (plan.custom(Hook.FINISH)) invoke(Hook.FINISH, c -> { policy.onFinish(c, state, beforeFinish); return null; }); }
            catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); reportFault(error); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
        } finally {
            try { backend.flush(); }
            catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
            finally {
                try { backend.release(); }
                catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
                finally {
                    try { afterRelease.run(); }
                    catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
                    afterRelease = () -> {};
                    outcome = snapshot(stoppedAt, reason, failure);
                    closed = true;
                    selected = Collections.emptyList(); batches = Collections.emptyList(); candidates.clear(); candidates = null; state = null; policy = null; plan = null; backend = null; request = null; configuration = null;
                    Runnable done = completion; completion = () -> {};
                    finishing = false;
                    done.run();
                }
            }
        }
    }
    private void reportFault(Throwable failure) {
        try { backend.fault(failure); }
        catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); }
    }
    private SweepOutcome snapshot(SweepPhase stoppedAt, String reason, Throwable failure) {
        return new SweepOutcome(phase, stoppedAt, reason, failure == null ? null : message(failure), scanned, selectedCount,
                processed, skipped, failed, committed, pendingRemoval, pendingPersistence, diagnostics);
    }
    private static String message(Throwable error) { return error.getMessage() == null ? error.getClass().getName() : error.getMessage(); }
    static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
        if (error instanceof ThreadDeath) throw (ThreadDeath) error;
    }
    private static final class Cancelled extends RuntimeException {
        Cancelled(String reason) { super(reason, null, false, false); }
    }

    private List<EntityAccess> collectDefault() {
        return request.candidates() != null ? request.candidates() : backend.collect(request, scope, this::checkEntityView);
    }
    private List<EntityAccess> filterDefault(List<EntityEvaluation> evaluations) {
        List<EntityAccess> defaults = new ArrayList<>();
        List<DefaultSweepFilter.Entry<EntityAccess, Object>> scans = new ArrayList<>();
        for (EntityEvaluation e : evaluations) if (!e.skipped() && e.disposition() == EntityDisposition.DEFAULT) defaults.add(e.entity());
        if (!request.ignoreFilter() && !request.preFiltered()) for (EntityAccess e : defaults) {
            SweepCandidates.Record record = candidates.get(e);
            scans.add(Objects.requireNonNull(record == null ? null : record.scan, "missing default scan"));
        }
        List<EntityAccess> chosen = request.ignoreFilter() || request.preFiltered() ? defaults : backend.filter(request, scans);
        Set<Object> chosenIds = Collections.newSetFromMap(new IdentityHashMap<>());
        for (EntityAccess e : chosen) chosenIds.add(backend.identity(e));
        List<EntityAccess> result = new ArrayList<>();
        for (EntityEvaluation e : evaluations) if (!e.skipped() && (e.disposition() == EntityDisposition.CLEAN
                || e.disposition() == EntityDisposition.DEFAULT && chosenIds.contains(backend.identity(e.entity())))) result.add(e.entity());
        return result;
    }

    private final class Context implements SweepDefaults.Access {
        private final long frame;
        Context(long frame) { this.frame = frame; }
        private void check() {
            checkOwner();
            if (closed || hook == null || frame != callback) throw new IllegalStateException("Sweep callback context expired");
        }
        public UUID taskId() { check(); return id; }
        public long generationId() { check(); return generationId; }
        public SweepTrigger trigger() { check(); return request.trigger(); }
        public SweepOperation operation() { check(); return request.operation(); }
        public SweepPhase phase() { check(); return phase; }
        public String cancellationReason() { check(); return cancellationReason; }
        public SweepScope scope() { check(); return scope; }
        public boolean ignoreFilter() { check(); return request.ignoreFilter(); }
        public boolean preFiltered() { check(); return request.preFiltered(); }
        public Map<String, Object> configuration() { check(); return configuration == null ? Collections.emptyMap() : configuration; }
        public ServerAccess server() { check(); return backend.server(this::check, SweepTask.this::checkEntityView); }
        public NativeMembers members() { check(); return backend.members(); }
        public void requestScope(SweepScope requested) {
            check();
            if (hook != Hook.BEFORE_COLLECT) throw new IllegalStateException("Scope changes require beforeCollect");
            if (!Objects.requireNonNull(requested, "scope").within(request.scope())) throw new IllegalArgumentException("Scope exceeds authorization");
            scope = requested;
        }
        public List<EntityAccess> collectDefault() {
            check(); if (hook != Hook.COLLECT) throw new IllegalStateException("Default collection outside collectEntities");
            return SweepTask.this.collectDefault();
        }
        public List<EntityAccess> filterDefault(List<EntityEvaluation> evaluations) {
            check(); if (hook != Hook.FILTER) throw new IllegalStateException("Default filtering outside finishFiltering");
            return SweepTask.this.filterDefault(evaluations);
        }
        SweepEntityContext entity(EntityAccess entity, int index) {
            return new SweepEntityContext() {
                public SweepContext task() { check(); return Context.this; }
                public EntityAccess entity() { check(); return entity; }
                public int index() { check(); return index; }
            };
        }
        BatchContext batch(List<EntityAccess> entities) {
            final int index = batchIndex, total = batchCount;
            return new BatchContext() {
                public SweepContext task() { check(); return Context.this; }
                public int index() { check(); return index; }
                public int total() { check(); return total; }
                public List<EntityAccess> entities() { check(); return entities; }
            };
        }
    }
}
