package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumListType;
import xin.vanilla.aotake.internal.forge.rule.ForgeEntityRuleContext;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.common.config.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated bounded dev mode; no world or cleanup side effects. */
final class AotakeRuleManagerSmokeClientRunner {
    private static ConfigHolder holder;
    private static ConfigEditSnapshot original;
    private static RuleManager<Entity> manager;
    private static RuleGeneration<Entity> generation;
    private static ExecutorService worker;
    private static CompletableFuture<Long> pending;
    private static RuleFileStore store;
    private static Path sourcePath;
    private static String source;
    private static int phase;
    private static long started;
    private static boolean finished;

    private AotakeRuleManagerSmokeClientRunner() { }

    static void tick(Minecraft client) {
        if (finished) return;
        try {
            if (holder == null) {
                holder = BaniraConfig.holder(CommonConfig.class);
                original = holder.snapshotForEdit(RuleMigrationTransaction.PATHS);
                Map<String,Object> seed = new LinkedHashMap<>();
                for (String path : RuleMigrationTransaction.LISTS) seed.put(path, Collections.singletonList("tick -> tick >= 5"));
                for (String path : RuleMigrationTransaction.MODES) seed.put(path, EnumListType.WHITE);
                holder.setAll(seed, ConfigEditOrigin.API); holder.save();
                Path directory = Paths.get(System.getProperty("aotake.scriptSmoke.status")).getParent().resolve("manager-" + UUID.randomUUID());
                store = new RuleFileStore(directory);
                worker = Executors.newSingleThreadExecutor(task -> new Thread(task, "aotake-rule-manager-smoke"));
                ScriptSession<EntityRule> session = BaniraScripts.open("aotake_sweep", EntityRule.class, RuleSource.CONTRACT_VERSION, ScriptLimits.defaults(), client::execute);
                manager = new RuleManager<>(store, holder, session, worker, client::execute, ForgeEntityRuleContext::new);
                expectBlocked(() -> manager.acquire(RuleManager.RuleUse.SWEEP));
                pending = manager.reload(); started = System.nanoTime();
            } else if (pending.isDone()) {
                advance(client);
            } else if (System.nanoTime() - started > 30_000_000_000L) throw new IllegalStateException("Manager smoke timed out");
        } catch (Throwable failure) {
            failure.printStackTrace(); finish(client, "FAIL " + failure + "\n");
        }
    }

    private static void advance(Minecraft client) throws Exception {
        ItemEntity entity = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.STONE));
        entity.tickCount = 8;
        if (phase == 0) {
            pending.join();
            generation = manager.acquire(RuleManager.RuleUse.SWEEP);
            for (RuleGeneration.RuleList list : RuleGeneration.RuleList.values())
                require(generation.matches(list, entity), "Native rule failed: " + list);
            require(!generation.shouldClean(false, entity), "WHITE match was not protected");
            String id = store.loadCatalog().sources().keySet().iterator().next();
            sourcePath = store.root().resolve("rules/sources/" + id + ".java");
            source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
            writeSource(source.replace("return ", "if (context.tick() < 0) throw new IllegalStateException(\"probe\"); return "));
            pending = manager.reload(); phase = 1;
        } else if (phase == 1) {
            pending.join();
            expectBlocked(() -> generation.matches(RuleGeneration.RuleList.SWEEP, entity));
            generation = manager.acquire(RuleManager.RuleUse.RECYCLE);
            entity.tickCount = -1;
            expectBlocked(() -> generation.matches(RuleGeneration.RuleList.CAPTURE, entity));
            for (RuleManager.RuleUse use : RuleManager.RuleUse.values()) expectBlocked(() -> manager.acquire(use));
            require(!manager.isUsable(generation.id()), "Faulted generation remained usable");
            writeSource(source);
            pending = manager.reload(); phase = 2;
        } else if (phase == 2) {
            pending.join();
            generation = manager.acquire(RuleManager.RuleUse.SWEEP);
            require(generation.matches(RuleGeneration.RuleList.SWEEP, entity), "Repaired rule was not published");
            writeSource("invalid Java source");
            pending = manager.reload(); phase = 3;
        } else if (phase == 3) {
            require(pending.isCompletedExceptionally(), "Invalid source compiled");
            require(manager.isUsable(generation.id()), "Same-selection failure discarded working rules");
            require(generation.matches(RuleGeneration.RuleList.SWEEP, entity), "Retained rule changed");
            holder.set(RuleMigrationTransaction.MODES.get(0), EnumListType.BLACK);
            require(!manager.isUsable(generation.id()), "Mode change failed to invalidate delayed batch");
            pending = manager.reload(); phase = 4;
        } else {
            require(pending.isCompletedExceptionally(), "Invalid replacement accepted");
            expectBlocked(() -> manager.acquire(RuleManager.RuleUse.SWEEP));
            require(!entity.removed && entity.getItem().getCount() == 1, "Probe changed entity");
            finish(client, "PASS native-rule-generation-publication-fault-reload-mode-change-release\nFINISHED manager-client\n");
        }
    }

    private static void writeSource(String text) throws Exception { Files.write(sourcePath, text.getBytes(StandardCharsets.UTF_8)); }
    private static void require(boolean success, String message) { if (!success) throw new IllegalStateException(message); }
    private static void expectBlocked(Runnable action) {
        try { action.run(); throw new AssertionError("Rule operation was not blocked"); }
        catch (RuleExecutionException expected) { }
    }

    private static void finish(Minecraft client, String result) {
        finished = true;
        try {
            if (manager != null) manager.close();
            if (worker != null) worker.shutdownNow();
            if (original != null) {
                ConfigCommitResult restored = holder.compareAndSetAll(holder.snapshotForEdit(RuleMigrationTransaction.PATHS), original.getValues(), ConfigEditOrigin.LOCAL_MIGRATION);
                require(restored != ConfigCommitResult.CONFLICT, "Smoke restore conflicted");
                require(original.getValues().equals(holder.snapshotForEdit(RuleMigrationTransaction.PATHS).getValues()), "Smoke restore failed");
            }
        } catch (Throwable failure) { failure.printStackTrace(); result = "FAIL restore " + failure + "\n"; }
        finally { holder = null; original = null; manager = null; generation = null; worker = null; pending = null; store = null; source = null; sourcePath = null; }
        try { Files.write(Paths.get(System.getProperty("aotake.scriptSmoke.status")), result.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception failure) { throw new IllegalStateException("Cannot write manager smoke status", failure); }
        finally { client.stop(); }
    }
}
