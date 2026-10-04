package xin.vanilla.aotake.internal.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import xin.vanilla.aotake.AotakeLang;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.api.rule.EntityAccess;
import xin.vanilla.aotake.api.sweep.SweepOperation;
import xin.vanilla.aotake.api.sweep.SweepPhase;
import xin.vanilla.aotake.api.sweep.SweepRequest;
import xin.vanilla.aotake.api.sweep.SweepTrigger;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.internal.forge.rule.ForgeEntityAccess;
import xin.vanilla.aotake.internal.forge.sweep.ForgeSweepBackend;
import xin.vanilla.aotake.internal.forge.sweep.ForgeSweepTarget;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.aotake.rule.RuleGeneration;
import xin.vanilla.aotake.sweep.SweepCoordinator;
import xin.vanilla.aotake.sweep.SweepExecution;
import xin.vanilla.aotake.sweep.SweepTask;
import xin.vanilla.aotake.sweep.SweepTaskFactory;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.common.util.MessageUtils;

import java.util.*;

/**
 * Server-only queue. Public cleanup entrypoints submit here, including nested policy requests.
 */
public final class AotakeSweepRuntime {
    private static AotakeSweepRuntime current;
    private final MinecraftServer server;
    private final SweepCoordinator coordinator;
    private final Map<UUID, Submission> pending = new HashMap<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private UUID chunkTask;
    private long tick;

    private AotakeSweepRuntime(MinecraftServer server) {
        this.server = server;
        coordinator = new SweepCoordinator((id, request) -> {
            Submission submission = Objects.requireNonNull(pending.remove(id), "submission");
            Job job = new Job(request, submission);
            jobs.put(id, job);
            job.task = SweepTaskFactory.create(id, request, AotakeRuleRuntime.manager(), lease -> {
                if (submission.expected != null && submission.expected.id() != lease.generationId())
                    throw new IllegalStateException("Stale submitted generation");
                return job.backend = new ForgeSweepBackend(server, AotakeRuleRuntime.manager(), lease, request, submission.target, submission.sweeper, submission.result);
            });
            return job.task;
        }, ready -> {
            Job job = jobs.get(ready.taskId());
            ForgeSweepBackend backend = job.backend;
            boolean chunk = job.request.trigger() == SweepTrigger.CHUNK;
            if (chunk) EventHandlerProxy.notifyChunkCheck(server, backend.noticeGroups(), backend.settings().chunk());
            boolean scanOnly = chunk && (backend.settings().chunk().chunkCheckOnlyNotice() || ready.selectedCount() == 0);
            scanOnly |= job.request.operation() == SweepOperation.RECYCLE && backend.noOnlinePlayer();
            job.execution = new SweepExecution(job.task, ready, tick, chunk ? 25 : 0, backend.settings().batch().sweepEntityInterval(), scanOnly);
        }, (id, error) -> {
            pending.remove(id);
            jobs.remove(id);
            if (id.equals(chunkTask)) chunkTask = null;
            AotakeUtils.reportSweepFailure(new IllegalStateException("Sweep dispatch failed", error));
        }, task -> {
            Job job = jobs.remove(task.id());
            if (task.id().equals(chunkTask)) chunkTask = null;
            if (job == null) return;
            if (task.phase() == SweepPhase.FAILED) {
                AotakeUtils.reportSweepFailure(new IllegalStateException(task.outcome().failure()));
                return;
            }
            if (task.phase() != SweepPhase.SUCCESS || task.outcome().stoppedAt() != SweepPhase.RUNNING) return;
            if (job.request.operation() == SweepOperation.RECYCLE)
                job.submission.sweeper.notifyResult(job.submission.result, job.backend.settings());
            else if (job.request.operation() == SweepOperation.DELETE)
                for (ServerPlayer player : server.getPlayerList().getPlayers())
                    MessageUtils.sendNotification(player, AotakeUtils.getWarningMessage(job.submission.result.isEmpty() ? "fail" : "success", AotakeLang.getPlayerLanguage(player), job.submission.result), AotakeNotificationTypes.ADMIN_BROADCAST);
        });
    }

    public static void start(MinecraftServer server) {
        if (current != null) throw new IllegalStateException("Sweep server already running");
        current = new AotakeSweepRuntime(server);
    }

    public static void tick() {
        AotakeSweepRuntime runtime = current;
        if (runtime == null) return;
        runtime.tick++;
        runtime.coordinator.tick();
        for (Job job : new ArrayList<>(runtime.jobs.values()))
            if (job.execution != null) job.execution.tick(runtime.tick);
    }

    public static void stop() {
        AotakeSweepRuntime runtime = current;
        if (runtime == null) return;
        try {
            runtime.coordinator.stop();
        } finally {
            runtime.pending.clear();
            runtime.jobs.clear();
            runtime.chunkTask = null;
            current = null;
        }
    }

    public static boolean chunkPending() {
        return current != null && current.chunkTask != null;
    }

    public static UUID submit(SweepTrigger trigger, SweepOperation operation, ForgeSweepTarget target, boolean ignoreFilter,
                              List<Entity> entities, boolean filtered, SweepResult result, EntitySweeper sweeper, RuleGeneration<Entity> expected) {
        AotakeSweepRuntime runtime = current;
        if (runtime == null) throw new IllegalStateException("Sweep server is not running");
        if (!runtime.server.isSameThread()) throw new IllegalStateException("Sweep requires server thread");
        SweepRequest.Builder builder = SweepRequest.builder(trigger, operation).scope(target.scope()).ignoreFilter(ignoreFilter).preFiltered(filtered);
        if (entities != null) {
            List<EntityAccess> candidates = new ArrayList<>();
            for (Entity entity : entities)
                if (entity != null) candidates.add(new ForgeEntityAccess(entity, () -> {
                }));
            builder.candidates(candidates);
        }
        UUID id = runtime.coordinator.submit(builder.build());
        runtime.pending.put(id, new Submission(target, result, sweeper, expected));
        if (trigger == SweepTrigger.CHUNK) runtime.chunkTask = id;
        return id;
    }

    public static void submitChunk() {
        if (chunkPending()) return;
        submit(SweepTrigger.CHUNK, SweepOperation.RECYCLE, ForgeSweepTarget.all(), false, null, false, new SweepResult().setChunkOverloadVault(true), AotakeSweep.getEntitySweeper(), null);
    }

    private static final class Submission {
        final ForgeSweepTarget target;
        final SweepResult result;
        final EntitySweeper sweeper;
        final RuleGeneration<Entity> expected;

        Submission(ForgeSweepTarget target, SweepResult result, EntitySweeper sweeper, RuleGeneration<Entity> expected) {
            this.target = target;
            this.result = result;
            this.sweeper = sweeper;
            this.expected = expected;
        }
    }

    private static final class Job {
        final SweepRequest request;
        final Submission submission;
        SweepTask<?> task;
        ForgeSweepBackend backend;
        SweepExecution execution;

        Job(SweepRequest request, Submission submission) {
            this.request = request;
            this.submission = submission;
        }
    }
}
