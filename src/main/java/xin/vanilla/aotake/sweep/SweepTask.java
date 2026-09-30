package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.api.rule.*;
import xin.vanilla.aotake.api.sweep.*;
import java.util.*;
import java.util.function.Function;

/** Owner-thread state machine. No production cleanup entry point is implicit in this class. */
public final class SweepTask<S> {
    private enum Hook { STATE, BEFORE_COLLECT, COLLECT, AFTER_COLLECT, BEFORE_FILTER, BEFORE_ENTITY,
        EVALUATE, AFTER_ENTITY, FILTER, AFTER_FILTER, BEFORE_CLEANUP, BEFORE_BATCH, AFTER_BATCH, FINISH }
    private final UUID id;
    private final Thread owner = Thread.currentThread();
    private final long generationId;
    private SweepRequest request;
    private SweepBackend backend;
    private SweepPolicy<S> policy;
    private S state;
    private SweepScope scope;
    private Map<String, Object> configuration;
    private List<EntityAccess> selected = Collections.emptyList();
    private final IdentityHashMap<Object, DefaultSweepFilter.Entry<EntityAccess, Object>> defaultScans = new IdentityHashMap<>();
    private SweepPhase phase = SweepPhase.CREATED;
    private SweepOutcome outcome;
    private Runnable completion = () -> {};
    private final List<String> diagnostics = new ArrayList<>();
    private Hook hook;
    private long callback;
    private boolean closed, finishing, driving;
    private String cancellationReason;
    private int scanned, selectedCount, batchIndex, batchCount, offset;
    private int processed, skipped, failed, committed, pendingRemoval;
    private boolean pendingPersistence;

    public SweepTask(UUID id, SweepRequest request, SweepBackend backend, SweepPolicy<S> policy) {
        this.id = Objects.requireNonNull(id, "id"); this.request = Objects.requireNonNull(request, "request");
        this.backend = Objects.requireNonNull(backend, "backend"); this.policy = Objects.requireNonNull(policy, "policy");
        generationId = backend.generationId(); scope = request.scope();
    }
    public UUID id() { return id; }
    public SweepPhase phase() { return phase; }
    public SweepOutcome outcome() { return outcome; }
    void onCompletion(Runnable completion) { checkDriver(); this.completion = Objects.requireNonNull(completion); }
    void checkDriver() {
        checkOwner();
        if (hook != null || finishing || driving) throw new IllegalStateException("Recursive sweep execution");
    }
    private void checkOwner() { if (Thread.currentThread() != owner) throw new IllegalStateException("Sweep requires owner thread"); }
    private void checkEntityView() {
        checkOwner();
        if (closed) throw new IllegalStateException("Sweep task has ended");
        backend.requireUsable();
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
            state = invoke(Hook.STATE, c -> policy.createState(c));
            checkpoint();
            phase = SweepPhase.COLLECTING;
            requireContinue(step(Hook.BEFORE_COLLECT, c -> policy.beforeCollect(c, state)), "beforeCollect");
            List<EntityAccess> candidates = new ArrayList<>(Objects.requireNonNull(step(Hook.COLLECT, c -> policy.collectEntities(c, state)), "candidates"));
            step(Hook.AFTER_COLLECT, c -> { policy.afterCollect(c, state, candidates); return null; });
            List<EntityAccess> admitted = normalize(candidates);
            scanned = admitted.size();
            phase = SweepPhase.FILTERING;
            requireContinue(step(Hook.BEFORE_FILTER, c -> policy.beforeFilter(c, state, Collections.unmodifiableList(admitted))), "beforeFilter");
            List<EntityEvaluation> evaluations = new ArrayList<>(admitted.size());
            for (int i = 0; i < admitted.size(); i++) {
                final int index = i;
                EntityAccess entity = admitted.get(i);
                FlowControl control = Objects.requireNonNull(step(Hook.BEFORE_ENTITY, c -> policy.beforeEntity(c.entity(entity, index), state)), "beforeEntity result");
                if (control == FlowControl.CANCEL) throw new Cancelled("beforeEntity");
                EntityEvaluation evaluated = control == FlowControl.SKIP ? EntityEvaluation.skipped(entity)
                        : EntityEvaluation.of(entity, step(Hook.EVALUATE, c -> policy.evaluateEntity(c.entity(entity, index), state)));
                captureDefault(evaluated);
                EntityEvaluation adjusted = Objects.requireNonNull(step(Hook.AFTER_ENTITY, c -> policy.afterEntity(c.entity(entity, index), state, evaluated)), "afterEntity result");
                if (backend.identity(adjusted.entity()) != backend.identity(entity)) throw new IllegalArgumentException("afterEntity changed entity identity");
                if (evaluated.skipped() && !adjusted.skipped()) throw new IllegalArgumentException("Skipped entity cannot be selected");
                if (adjusted.cancelled()) throw new Cancelled("afterEntity");
                EntityEvaluation canonical = adjusted.skipped() ? EntityEvaluation.skipped(entity) : EntityEvaluation.of(entity, adjusted.disposition());
                captureDefault(canonical);
                if (canonical.skipped() || canonical.disposition() != EntityDisposition.DEFAULT) defaultScans.remove(backend.identity(entity));
                evaluations.add(canonical);
            }
            List<EntityEvaluation> completed = Collections.unmodifiableList(evaluations);
            List<EntityAccess> choice = new ArrayList<>(Objects.requireNonNull(step(Hook.FILTER, c -> policy.finishFiltering(c, state, completed)), "selected"));
            validateSelection(choice, completed);
            step(Hook.AFTER_FILTER, c -> { policy.afterFilter(c, state, choice); return null; });
            selected = validateSelection(choice, completed);
            selectedCount = selected.size();
            if (request.operation() == SweepOperation.SCAN_ONLY) { finish(SweepPhase.SUCCESS, "scan complete", null); return null; }
            phase = SweepPhase.READY;
            return new PreparedSweep(this, generationId);
        } catch (RuntimeException | Error error) { terminate(error); return null; }
        finally { driving = false; }
    }

    private void captureDefault(EntityEvaluation evaluation) {
        if (evaluation.skipped() || evaluation.disposition() != EntityDisposition.DEFAULT || request.ignoreFilter() || request.preFiltered()) return;
        Object identity = backend.identity(evaluation.entity());
        if (!defaultScans.containsKey(identity)) {
            checkpoint();
            defaultScans.put(identity, Objects.requireNonNull(backend.evaluate(request, evaluation.entity()), "default evaluation"));
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
    private List<EntityAccess> validateSelection(List<EntityAccess> choice, List<EntityEvaluation> completed) {
        IdentityHashMap<Object, EntityAccess> allowed = new IdentityHashMap<>();
        for (EntityEvaluation e : completed) if (!e.skipped()) allowed.put(backend.identity(e.entity()), e.entity());
        return restrict(choice, allowed);
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
            requireContinue(step(Hook.BEFORE_CLEANUP, c -> policy.beforeCleanup(c, state, Collections.unmodifiableList(selected))), "beforeCleanup");
            phase = SweepPhase.RUNNING;
            batchCount = selected.isEmpty() ? (request.operation() == SweepOperation.RECYCLE ? 1 : 0)
                    : (selected.size() - 1) / request.batchSize() + 1;
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
            int end = offset + Math.min(request.batchSize(), selected.size() - offset);
            List<EntityAccess> entities = Collections.unmodifiableList(selected.subList(offset, end));
            SweepBackend.Progress progress = new SweepBackend.Progress(entities.size());
            Throwable failure = null;
            boolean cancel = false;
            try {
                FlowControl control = Objects.requireNonNull(step(Hook.BEFORE_BATCH, c -> policy.beforeBatch(c.batch(entities), state)), "beforeBatch result");
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
            try { invoke(Hook.AFTER_BATCH, c -> { policy.afterBatch(c.batch(entities), state, result); return null; }); }
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
            try { invoke(Hook.FINISH, c -> { policy.onFinish(c, state, beforeFinish); return null; }); }
            catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); reportFault(error); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
        } finally {
            try { backend.flush(); }
            catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
            finally {
                try { backend.release(); }
                catch (RuntimeException | Error error) { rethrowFatal(error); diagnostics.add(message(error)); if (failure == null) { failure = error; phase = SweepPhase.FAILED; } }
                finally {
                    outcome = snapshot(stoppedAt, reason, failure);
                    closed = true;
                    selected = Collections.emptyList(); defaultScans.clear(); state = null; policy = null; backend = null; request = null; configuration = null;
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
        public ServerAccess server() { check(); return backend.server(this::check); }
        public NativeMembers members() { check(); return backend.members(); }
        public void requestScope(SweepScope requested) {
            check();
            if (hook != Hook.BEFORE_COLLECT) throw new IllegalStateException("Scope changes require beforeCollect");
            if (!Objects.requireNonNull(requested, "scope").within(request.scope())) throw new IllegalArgumentException("Scope exceeds authorization");
            scope = requested;
        }
        public List<EntityAccess> collectDefault() {
            check(); if (hook != Hook.COLLECT) throw new IllegalStateException("Default collection outside collectEntities");
            return request.candidates() != null ? request.candidates() : backend.collect(request, scope, SweepTask.this::checkEntityView);
        }
        public List<EntityAccess> filterDefault(List<EntityEvaluation> evaluations) {
            check(); if (hook != Hook.FILTER) throw new IllegalStateException("Default filtering outside finishFiltering");
            List<EntityAccess> defaults = new ArrayList<>();
            List<DefaultSweepFilter.Entry<EntityAccess, Object>> scans = new ArrayList<>();
            for (EntityEvaluation e : evaluations) if (!e.skipped() && e.disposition() == EntityDisposition.DEFAULT) defaults.add(e.entity());
            if (!request.ignoreFilter() && !request.preFiltered()) for (EntityAccess e : defaults) {
                scans.add(Objects.requireNonNull(defaultScans.get(backend.identity(e)), "missing default scan"));
            }
            List<EntityAccess> chosen = request.ignoreFilter() || request.preFiltered() ? defaults : backend.filter(request, scans);
            Set<Object> chosenIds = Collections.newSetFromMap(new IdentityHashMap<>());
            for (EntityAccess e : chosen) chosenIds.add(backend.identity(e));
            List<EntityAccess> result = new ArrayList<>();
            for (EntityEvaluation e : evaluations) if (!e.skipped() && (e.disposition() == EntityDisposition.CLEAN
                    || e.disposition() == EntityDisposition.DEFAULT && chosenIds.contains(backend.identity(e.entity())))) result.add(e.entity());
            return result;
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
