package xin.vanilla.aotake.rule;

import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.banira.api.script.ScriptSession;
import xin.vanilla.banira.common.config.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Server-owned rule publication. Construct, acquire, reload and close on the server thread. */
public final class RuleManager<S> implements AutoCloseable {
    public enum RuleUse { SWEEP, CHUNK, RECYCLE }

    private final Thread ownerThread = Thread.currentThread();
    private final Executor owner;
    private final ConfigHolder holder;
    private final ScriptSession<EntityRule> session;
    private final RuleMigrationTransaction migration;
    private final RuleReloadCoordinator reloads;
    private final Function<RuleBindingPlan,LazyRuleContext<S>> contexts;
    private final List<Runnable> unregister = new ArrayList<>();
    private final ThreadLocal<RuleCatalog> committing = new ThreadLocal<>();
    private volatile RuleCatalog approved;
    private volatile boolean closed;
    private RuleGeneration<S> active;
    private CompletableFuture<Long> pending;
    private boolean publishing, reloadAfterPublication;
    private long sequence;
    private String unavailable = "Rules have not been loaded";

    public RuleManager(RuleFileStore store, ConfigHolder holder, ScriptSession<EntityRule> session,
                       Executor worker, Executor owner, Function<RuleBindingPlan,LazyRuleContext<S>> contexts) {
        this.holder = Objects.requireNonNull(holder);
        this.session = Objects.requireNonNull(session);
        this.owner = Objects.requireNonNull(owner);
        this.contexts = Objects.requireNonNull(contexts);
        // Native binding construction is checked on the owner before committing the candidate.
        migration = new RuleMigrationTransaction(store, holder, session, plan -> {});
        reloads = new RuleReloadCoordinator(worker, owner, () -> migration.prepare(holder.snapshotForEdit(RuleMigrationTransaction.PATHS), true));
        unregister.add(holder.onEdit(this::validateEdit));
        unregister.add(holder.onSaved(this::configChanged));
        unregister.add(holder.onReloaded(this::configChanged));
    }

    public RuleGeneration<S> acquire(RuleUse use) {
        checkOwner();
        Objects.requireNonNull(use);
        refreshSelection();
        if (active == null) throw new RuleExecutionException(-1, unavailable);
        active.requireUsable();
        return active;
    }

    public boolean isUsable(long generationId) {
        checkOwner();
        if (!closed) refreshSelection();
        return !closed && active != null && active.id() == generationId && active.isUsable();
    }

    public CompletableFuture<Long> reload() {
        checkOwner();
        if (closed) return failed(new CancellationException("Rule manager closed"));
        refreshSelection();
        if (publishing) {
            reloadAfterPublication = true;
            return pending;
        }
        // A rejected owner dispatch can complete the observer off-thread, but state stays owner-owned.
        if (pending != null && pending.isDone()) pending = null;
        if (pending != null) {
            reloads.requestReload();
            return pending;
        }
        CompletableFuture<Long> result = new CompletableFuture<>();
        pending = result;
        prepare(result);
        return result;
    }

    private void prepare(CompletableFuture<Long> result) {
        reloads.requestReload().whenComplete((candidate, failure) -> {
            try {
                // Coordinator delivery and publication share one owner task: no stale-candidate gap.
                if (Thread.currentThread() == ownerThread) finish(result, candidate, failure);
                else owner.execute(() -> finish(result, candidate, failure));
            }
            catch (RuntimeException rejected) { result.completeExceptionally(rejected); }
        });
    }

    private void finish(CompletableFuture<Long> result, RuleMigrationTransaction.PreparedMigration candidate, Throwable failure) {
        checkOwner();
        if (closed) { result.completeExceptionally(new CancellationException("Rule manager closed")); return; }
        if (pending != result) return;
        RuleGeneration<S> replacement = null;
        Throwable rejected = null;
        boolean published = false;
        publishing = true;
        try {
            if (failure != null) throw new CompletionException(failure);
            Map<String,Object> values = new LinkedHashMap<>(candidate.snapshot().getValues());
            values.putAll(candidate.changes());
            replacement = new RuleGeneration<>(++sequence, new RuleGeneration.Selection(values), candidate.catalog(), candidate.compiled().scripts(), contexts);
            committing.set(candidate.catalog());
            ConfigCommitResult commit;
            try { commit = migration.commit(candidate); }
            finally { committing.remove(); }
            if (commit == ConfigCommitResult.CONFLICT) throw new IllegalStateException("Configuration changed during rule preparation");
            if (!replacement.hasSelection(selection())) throw new IllegalStateException("Rule selection changed before publication");
            // Save listeners may request another reload (or stop the world) inside commit.
            if (!closed && !reloadAfterPublication) {
                if (!session.publish(candidate.compiled())) throw new IllegalStateException("Compiled candidate expired before publication");
                if (active != null) active.retire("Replaced by rule generation " + replacement.id());
                active = replacement;
                approved = candidate.catalog();
                published = true;
            }
        } catch (Exception | LinkageError error) {
            rejected = root(error);
        } finally {
            publishing = false;
            if (!published && replacement != null) replacement.retire("Rule candidate was not published");
        }
        if (closed) return;
        refreshSelection();
        if (reloadAfterPublication) {
            reloadAfterPublication = false;
            prepare(result);
            return;
        }
        pending = null;
        if (rejected == null && active != null && active.isUsable()) result.complete(active.id());
        else {
            if (rejected == null) rejected = new RuleExecutionException(-1, "Rule selection changed after publication");
            unavailable = "Rule reload failed: " + rejected.getMessage();
            result.completeExceptionally(rejected);
        }
    }

    private void refreshSelection() {
        if (closed) throw new RuleExecutionException(-1, "Rule manager closed");
        if (active == null) return;
        boolean same;
        try { same = active.hasSelection(selection()); }
        catch (RuntimeException error) { same = false; }
        if (!same) {
            active.retire("Rule lists or modes changed; awaiting verified replacement");
            active = null;
            unavailable = "Rule lists or modes changed; awaiting verified replacement";
        }
    }

    private RuleGeneration.Selection selection() {
        Map<String,Object> values = new LinkedHashMap<>();
        synchronized (holder) {
            for (String path : RuleMigrationTransaction.PATHS) values.put(path, holder.get(path));
            return new RuleGeneration.Selection(values);
        }
    }

    private void validateEdit(ConfigEditOrigin origin, Map<String,Object> values) {
        RuleCatalog catalog = origin == ConfigEditOrigin.LOCAL_MIGRATION ? committing.get() : null;
        if (catalog == null) catalog = approved;
        if (catalog != null) { catalog.guard().validate(origin, values); return; }
        for (String path : RuleMigrationTransaction.LISTS) if (values.containsKey(path)) {
            Object value = values.get(path);
            if (!(value instanceof List)) throw new IllegalArgumentException("Expected rule list: " + path);
            for (Object rule : (List<?>) value) if (!(rule instanceof String) || !RuleCatalog.isResource((String) rule))
                throw new IllegalArgumentException("Rule has not been locally approved: " + rule);
        }
    }

    private void configChanged(Set<String> paths) {
        if (closed || committing.get() != null || Collections.disjoint(paths, RuleMigrationTransaction.PATHS)) return;
        owner.execute(() -> {
            if (!closed) reload().whenComplete((id, failure) -> {
                if (failure != null && !closed) org.apache.logging.log4j.LogManager.getLogger()
                        .error("Aotake rule reload failed; no new generation was activated", failure);
            });
        });
    }

    @Override public void close() {
        checkOwner();
        if (closed) return;
        closed = true;
        for (Runnable remove : unregister) remove.run();
        unregister.clear();
        reloads.close();
        session.close();
        approved = null;
        committing.remove();
        if (active != null) active.retire("Rule manager closed");
        active = null;
        if (pending != null) pending.completeExceptionally(new CancellationException("Rule manager closed"));
        pending = null;
    }

    private void checkOwner() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Rule manager must run on its owner thread");
    }
    private static Throwable root(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }
    private static <T> CompletableFuture<T> failed(Throwable error) {
        CompletableFuture<T> result = new CompletableFuture<>(); result.completeExceptionally(error); return result;
    }
}
