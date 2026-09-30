package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fml.loading.FMLEnvironment;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.internal.forge.rule.ForgeEntityRuleContext;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.aotake.rule.migration.*;
import xin.vanilla.banira.api.script.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Only enabled by the bounded dev task. Objects are never inserted into a world. */
public final class AotakeRuleSmokeClientRunner {
    private static ScriptSession<EntityRule> session;
    private static CompletableFuture<PreparedScripts<EntityRule>> pending;
    private static List<RuleSource> sources;
    private static boolean finished;
    private static long started;
    private AotakeRuleSmokeClientRunner() { }
    public static void tick(Minecraft client) {
        if (FMLEnvironment.production || !Boolean.getBoolean("aotake.scriptSmoke") || finished || client.getOverlay() != null) return;
        if ("migration".equals(System.getProperty("aotake.scriptSmoke.mode"))) {
            AotakeRuleMigrationSmokeClientRunner.tick(client);
            return;
        }
        try {
            if (session == null) {
                sources = Arrays.asList(source("create", "tick, clazz, itemClazz, createProcessing = [CreateData.Processing.Time] -> tick >= 5 && clazz :> itemClazz && (createProcessing <= 0 || createProcessing == null)"),
                        source("protection", "customName, hasOwner, createProcessing = [CreateData.Processing.Time] -> customName != null || hasOwner || createProcessing > 0"),
                        source("field", "v = {entity.tick} -> v == 8"));
                session = BaniraScripts.open("aotake_sweep", EntityRule.class, RuleSource.CONTRACT_VERSION, ScriptLimits.defaults(), client::execute);
                List<ScriptSource> scripts = new ArrayList<>(); sources.forEach(s -> scripts.add(s.script()));
                pending = session.prepare(scripts); started = System.nanoTime();
            } else if (pending.isDone()) {
                PreparedScripts<EntityRule> candidate = pending.join();
                if (!session.publish(candidate)) throw new IllegalStateException("Candidate rejected");
                verify(session.active());
                finish(client, "PASS default-rules-native-item-nbt-field-release\nFINISHED rule-client\n");
            } else if (System.nanoTime() - started > 30_000_000_000L) throw new IllegalStateException("Compilation timed out");
        } catch (Throwable error) { error.printStackTrace(); finish(client, "FAIL " + error + "\n"); }
    }
    private static RuleSource source(String id, String text) {
        RuleAst ast = LegacyRuleParser.parse(text);
        return JavaRuleEmitter.emit(id, ast, RuleTypeResolver.resolve(ast, RuleBindingPlan.defaults()));
    }
    private static void verify(Map<String,EntityRule> compiled) {
        ItemEntity entity = new ItemEntity(null, 0, 0, 0, new ItemStack(Items.STONE));
        entity.tickCount = 8;
        RuleBindingPlan plan = sources.get(0).bindings();
        ForgeEntityRuleContext context = new ForgeEntityRuleContext(plan);
        if (!context.evaluate(compiled.get("create"), entity, plan)) throw new IllegalStateException("Missing NBT default rule failed");
        CompoundNBT processing = new CompoundNBT(), create = new CompoundNBT();
        processing.putInt("Time", 3); create.put("Processing", processing); entity.getPersistentData().put("CreateData", create);
        if (context.evaluate(compiled.get("create"), entity, plan)) throw new IllegalStateException("Active Create item was matched");
        RuleBindingPlan protection = sources.get(1).bindings();
        ForgeEntityRuleContext protectedContext = new ForgeEntityRuleContext(protection);
        if (!protectedContext.evaluate(compiled.get("protection"), entity, protection)) throw new IllegalStateException("Create protection failed");
        processing.putInt("Time", 0);
        if (!context.evaluate(compiled.get("create"), entity, plan)) throw new IllegalStateException("Completed Create item missed");
        if (protectedContext.evaluate(compiled.get("protection"), entity, protection)) throw new IllegalStateException("Completed item stayed protected");
        RuleBindingPlan field = sources.get(2).bindings();
        if (!new ForgeEntityRuleContext(field).evaluate(compiled.get("field"), entity, field)) throw new IllegalStateException("Stable field alias failed");
        processing.putString("Time", "3");
        try { context.evaluate(compiled.get("create"), entity, plan); throw new AssertionError("Invalid NBT was coerced"); }
        catch (IllegalStateException expected) { }
        try { context.value(0, Integer.class); throw new AssertionError("Entity retained after failure"); }
        catch (IllegalStateException expected) { }
        entity.tickCount = 1;
        if (context.evaluate(compiled.get("create"), entity, plan)) throw new IllegalStateException("Short-circuit failed");
        if (entity.removed || entity.getItem().getCount() != 1) throw new IllegalStateException("Probe changed entity");
    }
    private static void finish(Minecraft client, String result) {
        finished = true;
        if (session != null) session.close();
        session = null; pending = null; sources = null;
        try {
            Path path = Paths.get(System.getProperty("aotake.scriptSmoke.status"));
            Files.createDirectories(path.getParent()); Files.write(path, result.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) { throw new IllegalStateException("Cannot write rule smoke status", error); }
        finally { client.stop(); }
    }
}
