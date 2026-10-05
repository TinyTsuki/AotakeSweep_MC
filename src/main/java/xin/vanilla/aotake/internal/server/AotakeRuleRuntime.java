package xin.vanilla.aotake.internal.server;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.api.sweep.SweepPolicy;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.neoforge.rule.NeoForgeEntityRuleContext;
import xin.vanilla.aotake.internal.neoforge.rule.NeoForgeNativeMembers;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.banira.api.script.BaniraScripts;
import xin.vanilla.banira.api.script.ScriptFactory;
import xin.vanilla.banira.api.script.ScriptLimits;
import xin.vanilla.banira.api.script.ScriptSession;
import xin.vanilla.banira.api.BaniraConfigs;

import java.util.concurrent.*;

/**
 * Only a running server owns executable rules; client config snapshots never enter here.
 */
public final class AotakeRuleRuntime {
    private static RuleManager<Entity> manager;
    private static ExecutorService worker;
    private static volatile MinecraftServer owner;

    private AotakeRuleRuntime() {
    }

    public static void start(MinecraftServer server) {
        if (server == null) return;
        if (owner != null) throw new IllegalStateException("Rule server is already running");
        owner = server;
        worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "aotake-local-rules");
            thread.setDaemon(true);
            return thread;
        });
        Executor executor = task -> {
            if (owner != server) throw new RejectedExecutionException("Rule server stopped");
            server.execute(() -> {
                if (owner == server) task.run();
            });
        };
        ScriptSession<EntityRule> scripts = BaniraScripts.open(AotakeSweep.MODID, EntityRule.class,
                RuleSource.CONTRACT_VERSION, ScriptLimits.defaults(), executor);
        ScriptSession<ScriptFactory<SweepPolicy>> policies = BaniraScripts.openFactorySession(AotakeSweep.MODID,
                SweepPolicy.class, "1", ScriptLimits.defaults(), executor);
        try {
            manager = new RuleManager<>(RuleFileStore.local(), BaniraConfigs.holder(CommonConfig.class), scripts,
                    worker, executor, NeoForgeEntityRuleContext::new, policies, NeoForgeNativeMembers::prepare);
        } catch (RuntimeException | Error failure) {
            scripts.close();
            policies.close();
            stop();
            throw failure;
        }
        reload().whenComplete((id, failure) -> {
            if (failure != null)
                LogManager.getLogger().error("Aotake rules unavailable; cleanup remains blocked", failure);
        });
    }

    public static CompletableFuture<Long> reload() {
        if (manager == null) {
            CompletableFuture<Long> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuleExecutionException(-1, "Rule server is not running"));
            return failed;
        }
        return manager.reload();
    }

    public static RuleGeneration<Entity> acquire(RuleManager.RuleUse use) {
        if (manager == null) throw new RuleExecutionException(-1, "Rule server is not running");
        return manager.acquire(use);
    }

    public static boolean isUsable(long id) {
        return manager != null && manager.isUsable(id);
    }

    public static boolean hasCompletedInitialLoad() {
        return manager != null && manager.hasCompletedInitialLoad();
    }

    static RuleManager<Entity> manager() {
        if (manager == null) throw new RuleExecutionException(-1, "Rule server is not running");
        return manager;
    }

    public static void stop() {
        if (manager != null) manager.close();
        manager = null;
        owner = null;
        if (worker != null) worker.shutdownNow();
        worker = null;
    }
}
