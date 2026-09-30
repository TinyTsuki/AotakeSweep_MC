package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.*;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.internal.forge.rule.ForgeEntityRuleContext;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.common.config.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated dev run only. Uses the smoke config and restores its original values before shutdown. */
final class AotakeRuleMigrationSmokeClientRunner {
    private static ConfigHolder holder;
    private static ConfigEditSnapshot original;
    private static ScriptSession<EntityRule> session;
    private static RuleMigrationTransaction transaction;
    private static CompletableFuture<RuleMigrationTransaction.PreparedMigration> pending;
    private static RuleFileStore store;
    private static Runnable unregister;
    private static String id;
    private static int phase;
    private static long started;
    private static boolean finished;
    private AotakeRuleMigrationSmokeClientRunner() { }
    static void tick(Minecraft client) {
        if (finished) return;
        try {
            if (holder == null) {
                holder = BaniraConfig.holder(CommonConfig.class); original = holder.snapshotForEdit(RuleMigrationTransaction.PATHS);
                Map<String,Object> seed = new LinkedHashMap<>();
                for (String path : RuleMigrationTransaction.LISTS) seed.put(path, Arrays.asList("minecraft:arrow", "tick -> tick >= 5"));
                holder.setAll(seed, ConfigEditOrigin.API); holder.save();
                Path evidence = Paths.get(System.getProperty("aotake.scriptSmoke.status")).getParent().resolve("migration-" + UUID.randomUUID());
                store = new RuleFileStore(evidence);
                session = BaniraScripts.open("aotake_sweep", EntityRule.class, RuleSource.CONTRACT_VERSION, ScriptLimits.defaults(), client::execute);
                transaction = new RuleMigrationTransaction(store, holder, session, ForgeEntityRuleContext::new);
                pending = transaction.prepare(holder.snapshotForEdit(RuleMigrationTransaction.PATHS)); started = System.nanoTime();
            } else if (pending.isDone()) {
                RuleMigrationTransaction.PreparedMigration candidate = pending.join();
                if (phase == 0) {
                    if (transaction.commit(candidate) != ConfigCommitResult.APPLIED) throw new IllegalStateException("Migration did not commit");
                    id = candidate.catalog().sources().keySet().iterator().next();
                    for (String path : RuleMigrationTransaction.LISTS) if (!Arrays.asList("minecraft:arrow", "script:" + id).equals(holder.get(path))) throw new IllegalStateException("Rule list missed: " + path);
                    unregister = holder.onEdit(candidate.catalog().guard());
                    ConfigEditSnapshot before = holder.snapshotForEdit(RuleMigrationTransaction.PATHS);
                    Map<String,Object> bad = new LinkedHashMap<>(); bad.put("base.sweep.entityListLimit", 41);
                    bad.put(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("tick -> tick > 1"));
                    try { holder.setAll(bad, ConfigEditOrigin.REMOTE); throw new AssertionError("Remote DSL accepted"); }
                    catch (IllegalArgumentException expected) { }
                    if (!before.getRevision().equals(holder.snapshotForEdit(RuleMigrationTransaction.PATHS).getRevision())) throw new IllegalStateException("Rejected edit changed config");
                    Path source = store.root().resolve("rules/sources/" + id + ".java");
                    String old = new String(Files.readAllBytes(source), StandardCharsets.UTF_8), edited = old.replace(">=5", ">=9");
                    if (old.equals(edited)) throw new IllegalStateException("Source fixture did not change");
                    Files.write(source, edited.getBytes(StandardCharsets.UTF_8));
                    pending = transaction.prepare(holder.snapshotForEdit(RuleMigrationTransaction.PATHS)); phase = 1;
                } else {
                    if (transaction.commit(candidate) != ConfigCommitResult.UNCHANGED) throw new IllegalStateException("Reload rewrote config");
                    if (!session.publish(candidate.compiled())) throw new IllegalStateException("Reload candidate not current");
                    ItemEntity entity = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.STONE));
                    RuleBindingPlan plan = candidate.catalog().sources().get(id).bindings(); ForgeEntityRuleContext context = new ForgeEntityRuleContext(plan);
                    entity.tickCount = 7; if (context.evaluate(session.active().get(id), entity, plan)) throw new IllegalStateException("Old rule remained active");
                    entity.tickCount = 10; if (!context.evaluate(session.active().get(id), entity, plan)) throw new IllegalStateException("Edited rule failed");
                    if (entity.removed || entity.getItem().getCount() != 1) throw new IllegalStateException("Smoke changed native entity");
                    finish(client, "PASS four-list-migration-remote-guard-local-source-reload\nFINISHED migration-client\n");
                }
            } else if (System.nanoTime() - started > 30_000_000_000L) throw new IllegalStateException("Migration smoke timed out");
        } catch (Throwable error) { error.printStackTrace(); finish(client, "FAIL " + error + "\n"); }
    }
    private static void finish(Minecraft client, String result) {
        finished = true;
        try {
            if (unregister != null) unregister.run();
            if (original != null) {
                ConfigCommitResult restored = holder.compareAndSetAll(holder.snapshotForEdit(RuleMigrationTransaction.PATHS), original.getValues(), ConfigEditOrigin.LOCAL_MIGRATION);
                if (restored == ConfigCommitResult.CONFLICT) throw new IllegalStateException("Smoke config restore conflicted");
                if (!original.getValues().equals(holder.snapshotForEdit(RuleMigrationTransaction.PATHS).getValues())) throw new IllegalStateException("Smoke config restore failed");
            }
        } catch (Throwable error) { error.printStackTrace(); result = "FAIL restore " + error + "\n"; }
        finally { if (session != null) session.close(); holder = null; original = null; session = null; pending = null; transaction = null; store = null; unregister = null; }
        try { Files.write(Paths.get(System.getProperty("aotake.scriptSmoke.status")), result.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception error) { throw new IllegalStateException("Cannot write migration smoke status", error); }
        finally { client.stop(); }
    }
}
