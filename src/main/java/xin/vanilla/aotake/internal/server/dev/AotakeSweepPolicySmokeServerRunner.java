package xin.vanilla.aotake.internal.server.dev;

import com.google.gson.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.api.sweep.*;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.world.*;
import xin.vanilla.aotake.event.EventHandlerProxy;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.internal.forge.sweep.ForgeSweepTarget;
import xin.vanilla.aotake.internal.server.*;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.aotake.util.*;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.config.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import static xin.vanilla.aotake.internal.dev.AotakePolicySmokeProbe.*;

/** Opt-in dedicated-server acceptance; all entities and source files belong to its isolated world. */
public final class AotakeSweepPolicySmokeServerRunner {
    private static int state, ticks, submittedAt, recordsBefore;
    private static long started, healthy;
    private static MinecraftServer server;
    private static ConfigHolder holder;
    private static CompletableFuture<Long> pending;
    private static final List<Entity> spawned = new ArrayList<>();
    private static SweepResult result;
    private static byte[] validMembers;
    private static Runnable unlisten;
    private static boolean invalidRemoteSaved;
    private AotakeSweepPolicySmokeServerRunner() { }

    public static void register() {
        if (!enabled()) return;
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) tickServer(BaniraServer.currentAs(MinecraftServer.class));
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) -> status(side(), "PASS server-shutdown"));
    }

    /** Also invoked by the separate local-only probe mod when testing the reobfuscated artifact. */
    public static void tickServer(MinecraftServer value) {
        if (value == null || !value.isRunning() || state == 99) return;
        server = value;
        try {
            if (started == 0) { started = System.nanoTime(); pending = AotakeRuleRuntime.reload(); }
            require(System.nanoTime() - started < 150_000_000_000L, "Policy smoke timed out in state " + state);
            ticks++;
            switch (state) {
                case 0:
                    if (!pending.isDone()) return;
                    pending.join(); holder = BaniraConfig.holder(CommonConfig.class);
                    if (restart()) {
                        require(vaultCount("policy_cancel") == 2, "Restart lost committed cancellation vault");
                        require("smoke".equals(holder.get(SweepPolicyCatalog.CONFIG_PATH)), "Restart lost policy selection");
                        pass("restart-policy-selection-and-cancelled-vault-persisted"); finish(); return;
                    }
                    installFixtures(); pending = AotakeRuleRuntime.reload(); state = 1; break;
                case 1:
                    if (!pending.isDone()) return;
                    pending.join();
                    holder.set(SweepPolicyCatalog.CONFIG_PATH, "smoke");
                    holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("script:native_probe"));
                    holder.set(RuleMigrationTransaction.LISTS.get(1), Collections.singletonList("script:native_probe"));
                    holder.save(); pending = AotakeRuleRuntime.reload(); state = 2; break;
                case 2:
                    if (!pending.isDone()) return;
                    pending.join();
                    ItemEntity probe = new ItemEntity(server.overworld(), 12, 100, 8, new ItemStack(Items.APPLE));
                    probe.tickCount = 42;
                    require(AotakeRuleRuntime.acquire(RuleManager.RuleUse.SWEEP).matches(RuleGeneration.RuleList.SWEEP, probe), "Native object/member mapping mismatch");
                    unlisten = holder.onSaved(paths -> invalidRemoteSaved |= ((Number) holder.get("base.batch.sweepBatchLimit")).intValue() == 999);
                    pass("native-source-object-identity-and-real-forge-member-mapping");
                    status("server", "READY policy-runtime"); state = 3; break;
                case 3:
                    if (!Boolean.getBoolean("aotake.policySmoke.standalone") && !has("client", "PASS policy-config-isolation")) {
                        if (!server.getPlayerList().getPlayers().isEmpty()) {
                            ServerPlayerEntity player = server.getPlayerList().getPlayers().get(0);
                            if (!player.hasPermissions(2)) server.getPlayerList().op(player.getGameProfile());
                        }
                        return;
                    }
                    require(!invalidRemoteSaved, "Invalid remote transaction partially saved");
                    begin("normal", 3); spawn(Items.DIAMOND, 1, 14);
                    holder.set("base.sweep.sweepInterval", 600000L); holder.save();
                    EventHandlerProxy.setNextSweepTime(System.currentTimeMillis() - 1);
                    state = 4; break;
                case 4:
                    if (!done()) return;
                    check("SCHEDULED", "RECYCLE", "SUCCESS", 3);
                    require(spawned.get(3).isAlive() && spawned.subList(0, 3).stream().noneMatch(Entity::isAlive), "Inherited KEEP/default filtering mismatch");
                    pass("scheduled-full-lifecycle-and-inherited-filter");
                    holder.set("base.sweep.sweepInterval", 0L); holder.save();
                    begin("normal", 2);
                    ArmorStandEntity stand = new ArmorStandEntity(server.overworld(), 8, 100, 8);
                    stand.setNoGravity(true); server.overworld().addFreshEntity(stand); spawned.add(stand);
                    int commandResult;
                    if (server.getPlayerList().getPlayers().isEmpty()) {
                        commandResult = server.getCommands().performCommand(server.createCommandSourceStack(), AotakeUtils.getCommand(EnumCommandType.CLEAR_DROP) + " minecraft:overworld false true");
                    } else {
                        ServerPlayerEntity player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(server.overworld(), 8, 100, 8, 0, 0);
                        spawn(Items.APPLE, 2, 60);
                        commandResult = server.getCommands().performCommand(player.createCommandSourceStack().withPermission(4), AotakeUtils.getCommand(EnumCommandType.CLEAR_DROP) + " 6 false true");
                    }
                    require(commandResult == 1, "DELETE command rejected"); state = 5; break;
                case 5:
                    if (!done()) return;
                    check("COMMAND", "DELETE", "SUCCESS", 2);
                    require(spawned.get(2).isAlive(), "withEntity=false removed a non-item");
                    if (spawned.size() == 4) require(spawned.get(3).isAlive(), "Range authorization widened");
                    pass("delete-command-range-and-entity-authorization");
                    begin("skip", 3); holder.set("base.batch.sweepEntityLimit", 1); holder.set("base.batch.sweepEntityInterval", 2); holder.save();
                    require(server.getCommands().performCommand(server.createCommandSourceStack(), AotakeUtils.getCommand(EnumCommandType.SWEEP) + " minecraft:overworld") == 1, "RECYCLE command rejected");
                    state = 6; break;
                case 6:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "SUCCESS", 2);
                    require(last().get("skipped").getAsInt() == 1 && alive() == 1, "Skipped batch was removed");
                    pass("manual-recycle-skip-continues-later-batches");
                    begin("normal", 5); holder.set("base.batch.sweepEntityLimit", 50);
                    holder.set("base.chunk.chunkCheckLimit", 2); holder.set("base.chunk.chunkCheckRetain", 0.5D);
                    holder.set("base.chunk.chunkCheckOnlyNotice", true); holder.save();
                    AotakeSweepRuntime.submitChunk(); state = 7; break;
                case 7:
                    if (!done()) return;
                    check("CHUNK", "RECYCLE", "SUCCESS", 0);
                    require(alive() == 5 && !last().get("events").toString().contains("beforeCleanup"), "Notice-only performed cleanup");
                    pass("chunk-notice-only-without-cleanup");
                    begin("normal", 5); holder.set("base.chunk.chunkCheckOnlyNotice", false); holder.save();
                    AotakeSweepRuntime.submitChunk(); state = 8; break;
                case 8:
                    if (ticks - submittedAt < 10) return;
                    require(alive() == 5, "Chunk cleanup ran before delay");
                    spawned.get(3).remove(); spawned.get(4).setPos(80, 100, 8);
                    holder.set("base.batch.sweepEntityLimit", 1); holder.save();
                    state = 9; break;
                case 9:
                    if (!done()) return;
                    check("CHUNK", "RECYCLE", "SUCCESS", 2);
                    require(last().get("delay").getAsLong() >= 25 && last().get("evaluated").getAsInt() == 5
                            && last().get("batchSize").getAsInt() == 50 && spawned.get(4).isAlive(), "Chunk delay/snapshot/revalidation mismatch");
                    pass("chunk-same-task-delay-snapshot-and-moved-entity-recheck");
                    begin("cancel", 2); AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 10; break;
                case 10:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "CANCELLED", 0);
                    require(alive() == 2, "beforeCleanup cancellation removed entities");
                    pass("before-cleanup-cancel-preserves-entities");
                    begin("normal", 3); holder.set("base.batch.sweepEntityInterval", 40); holder.save();
                    result = new SweepResult().setChunkOverloadVault(true).setChunkVaultTimePrefix("policy_cancel").setChunkVaultRunId("probe");
                    AotakeSweepRuntime.submit(SweepTrigger.COMMAND, SweepOperation.RECYCLE, ForgeSweepTarget.all(), false,
                            new ArrayList<>(spawned), false, result, AotakeSweep.getEntitySweeper(), null);
                    state = 11; break;
                case 11:
                    if (result.getBatch().get() == 0) return;
                    require(result.getBatch().get() == 1 && vaultCount("policy_cancel") == 2, "First batch did not persist once");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "WHITE"); holder.save();
                    pending = AotakeRuleRuntime.reload(); state = 12; break;
                case 12:
                    if (!done() || !pending.isDone()) return;
                    pending.join(); check("COMMAND", "RECYCLE", "CANCELLED", 1);
                    require(alive() == 2 && vaultCount("policy_cancel") == 2, "Generation cancellation lost or duplicated output");
                    pass("reload-cancels-remaining-batches-keeps-committed-vault");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK"); holder.save();
                    pending = AotakeRuleRuntime.reload(); state = 13; break;
                case 13:
                    if (!pending.isDone()) return;
                    healthy = pending.join();
                    Path manifest = RuleFileStore.local().root().resolve("rules/manifest.json");
                    validMembers = Files.readAllBytes(manifest);
                    Files.write(manifest, new String(validMembers, StandardCharsets.UTF_8).replace("func_226277_ct_", "missing_smoke_member").getBytes(StandardCharsets.UTF_8));
                    pending = AotakeRuleRuntime.reload(); state = 14; break;
                case 14:
                    if (!pending.isDone()) return;
                    require(pending.isCompletedExceptionally() && AotakeRuleRuntime.isUsable(healthy), "Bad member replaced healthy generation");
                    Files.write(RuleFileStore.local().root().resolve("rules/manifest.json"), validMembers);
                    pending = AotakeRuleRuntime.reload(); state = 15; break;
                case 15:
                    if (!pending.isDone()) return;
                    pending.join(); pass("bad-native-member-rejected-before-activation");
                    holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("minecraft:item"));
                    holder.set(RuleMigrationTransaction.LISTS.get(2), Collections.singletonList("script:native_probe"));
                    holder.save(); pending = AotakeRuleRuntime.reload(); state = 16; break;
                case 16:
                    if (!pending.isDone()) return;
                    pending.join(); begin("normal", 1);
                    System.setProperty("aotake.policySmoke.moveDuringRecycle", "true");
                    AotakeSweepRuntime.submit(SweepTrigger.COMMAND, SweepOperation.RECYCLE,
                            new ForgeSweepTarget("minecraft:overworld", new net.minecraft.util.math.AxisAlignedBB(0, 95, 0, 16, 110, 16), true),
                            false, new ArrayList<>(spawned), false, new SweepResult(), AotakeSweep.getEntitySweeper(), null);
                    state = 17; break;
                case 17:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "SUCCESS", 0);
                    require(alive() == 1 && spawned.get(0).getX() == 80, "Batch rule moved entity outside authorized bounds before commit");
                    pass("batch-rule-movement-revalidated-before-commit");
                    System.clearProperty("aotake.policySmoke.moveDuringRecycle");
                    holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("script:native_probe"));
                    holder.set(RuleMigrationTransaction.LISTS.get(2), Collections.emptyList());
                    holder.save(); pending = AotakeRuleRuntime.reload(); state = 18; break;
                case 18:
                    if (!pending.isDone()) return;
                    healthy = pending.join(); begin("removeBeforeFilter", 2);
                    AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 19; break;
                case 19:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "SUCCESS", 1);
                    require(AotakeRuleRuntime.isUsable(healthy), "Removed candidate faulted healthy generation");
                    pass("before-filter-removal-skips-invalid-candidate");
                    begin("removeBeforeEntity", 2); AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 20; break;
                case 20:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "SUCCESS", 1);
                    require(AotakeRuleRuntime.isUsable(healthy), "Entity hook removal faulted healthy generation");
                    pass("before-entity-removal-skips-invalid-candidate");
                    holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("minecraft:item"));
                    holder.set(RuleMigrationTransaction.LISTS.get(2), Collections.singletonList("script:native_probe"));
                    holder.save(); pending = AotakeRuleRuntime.reload(); state = 21; break;
                case 21:
                    if (!pending.isDone()) return;
                    healthy = pending.join(); begin("normal", 2);
                    System.setProperty("aotake.policySmoke.retireAt", "preflight");
                    AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 22; break;
                case 22:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "CANCELLED", 0);
                    require(alive() == 2 && last().get("failure").isJsonNull(), "Preflight retirement was a failure or removed entities");
                    pass("preflight-rule-retirement-is-cancellation");
                    System.clearProperty("aotake.policySmoke.retireAt");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK"); holder.save();
                    pending = AotakeRuleRuntime.reload(); state = 23; break;
                case 23:
                    if (!pending.isDone()) return;
                    healthy = pending.join(); begin("normal", 2);
                    System.setProperty("aotake.policySmoke.retireAt", "batch");
                    AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 24; break;
                case 24:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "CANCELLED", 0);
                    require(alive() == 2 && last().get("failure").isJsonNull(), "Batch retirement was a failure or removed entities");
                    require("RUNNING".equals(last().get("stoppedAt").getAsString()), "Batch retirement occurred before batch execution");
                    pass("batch-rule-retirement-is-cancellation");
                    System.clearProperty("aotake.policySmoke.retireAt");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK"); holder.save();
                    pending = AotakeRuleRuntime.reload(); state = 25; break;
                case 25:
                    if (!pending.isDone()) return;
                    healthy = pending.join(); begin("normal", 2);
                    System.setProperty("aotake.policySmoke.retireAt", "failure");
                    AotakeUtils.sweep(new ArrayList<>(spawned), false); state = 26; break;
                case 26:
                    if (!done()) return;
                    check("COMMAND", "RECYCLE", "FAILED", 0);
                    require(alive() == 2 && last().get("failure").getAsString().contains("failed in generation"), "Actual rule failure was swallowed as cancellation");
                    pass("actual-rule-error-after-retirement-remains-failure");
                    System.clearProperty("aotake.policySmoke.retireAt");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
                    holder.set(RuleMigrationTransaction.LISTS.get(0), Collections.singletonList("script:native_probe"));
                    holder.set(RuleMigrationTransaction.LISTS.get(2), Collections.emptyList());
                    holder.save(); pending = AotakeRuleRuntime.reload(); state = 27; break;
                case 27:
                    if (!pending.isDone()) return;
                    pending.join(); finish(); break;
                default: throw new IllegalStateException("Unknown policy smoke state " + state);
            }
        } catch (Throwable failure) { failure.printStackTrace(); status(side(), "FAIL state=" + state + " " + failure); finish(); }
    }

    public static void removeForSmoke(Object entity) { ((Entity) entity).remove(); }
    public static void retireForSmoke() {
        holder.set(RuleMigrationTransaction.MODES.get(0), "WHITE");
        require(!AotakeRuleRuntime.isUsable(healthy), "Rule selection did not retire current task");
        if ("failure".equals(System.getProperty("aotake.policySmoke.retireAt"))) throw new IllegalArgumentException("actual smoke rule failure");
    }

    private static void installFixtures() throws IOException {
        Path files = RuleFileStore.local().root();
        Path manifestFile = files.resolve("rules/manifest.json");
        JsonObject manifest = Files.exists(manifestFile) ? new JsonParser().parse(read(manifestFile)).getAsJsonObject() : new JsonObject();
        manifest.addProperty("schemaVersion", 1); manifest.addProperty("contractVersion", "1");
        JsonObject rule = new JsonObject(); rule.addProperty("className", "xin.vanilla.banira.generated.NativeEntityProbe");
        rule.addProperty("sourceHash", String.join("", Collections.nCopies(64, "0")));
        rule.add("nativeMembers", new JsonParser().parse(read(root().resolve("fixtures/NativeEntityProbe.members.json"))));
        JsonObject rules = manifest.has("rules") ? manifest.getAsJsonObject("rules") : new JsonObject();
        rules.add("native_probe", rule); manifest.add("rules", rules);
        write(files.resolve("rules/manifest.json"), manifest.toString());
        write(files.resolve("rules/bindings/native_probe.json"), "{\"schemaVersion\":1,\"contractVersion\":\"1\",\"slots\":[]}");
        copyFixture("NativeEntityProbe.java", files.resolve("rules/sources/native_probe.java"));
        copyFixture("SmokeSweepPolicy.java", files.resolve("rules/policies/sources/SmokeSweepPolicy.java"));
        copyFixture("PolicyAudit.java", files.resolve("rules/policies/sources/PolicyAudit.java"));
        write(files.resolve("rules/policies/manifest.json"), "{\"policySchema\":1,\"apiVersion\":\"1\",\"policies\":[{\"id\":\"smoke\",\"entryClassName\":\"xin.vanilla.banira.generated.SmokeSweepPolicy\",\"sourceFiles\":[\"SmokeSweepPolicy.java\",\"PolicyAudit.java\"],\"nativeMembers\":[]}]}");
    }
    private static void copyFixture(String name, Path destination) throws IOException { Files.createDirectories(destination.getParent()); Files.copy(root().resolve("fixtures/" + name), destination, StandardCopyOption.REPLACE_EXISTING); }
    private static void begin(String mode, int count) throws IOException {
        for (Entity entity : spawned) if (entity.isAlive()) entity.remove();
        spawned.clear(); recordsBefore = records().size(); submittedAt = ticks;
        System.setProperty("aotake.policySmoke.mode", mode);
        for (int i = 0; i < count; i++) spawn(Items.APPLE, 2, 8 + i);
    }
    private static void spawn(Item item, int count, double x) {
        ServerWorld world = server.overworld(); world.getChunk(((int) x) >> 4, 0);
        ItemEntity entity = new ItemEntity(world, x, 100, 8, new ItemStack(item, count));
        entity.tickCount = 20; entity.setNoGravity(true); entity.setDeltaMovement(0, 0, 0); entity.setPickUpDelay(6000);
        require(world.addFreshEntity(entity), "Fixture spawn failed"); spawned.add(entity);
    }
    private static long alive() { return spawned.stream().filter(Entity::isAlive).count(); }
    private static List<String> records() throws IOException { Path file = root().resolve("tasks.jsonl"); return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : Collections.emptyList(); }
    private static JsonObject last() throws IOException { List<String> entries = records(); return new JsonParser().parse(entries.get(entries.size() - 1)).getAsJsonObject(); }
    private static boolean done() throws IOException {
        require(ticks - submittedAt < 120, "Task timed out in state " + state);
        if (records().size() == recordsBefore) return false;
        EntitySweeper.flushPendingRemovals(server.overworld()); return true;
    }
    private static void check(String trigger, String operation, String status, int processed) throws IOException {
        require(records().size() == recordsBefore + 1, "Repeated task completion"); JsonObject value = last();
        require(trigger.equals(value.get("trigger").getAsString()) && operation.equals(value.get("operation").getAsString())
                && status.equals(value.get("status").getAsString()) && value.get("processed").getAsInt() == processed, "Unexpected task: " + value);
        JsonArray events = value.getAsJsonArray("events");
        require(events.get(0).getAsString().equals("create") && events.get(events.size() - 1).getAsString().equals("finish"), "Missing lifecycle endpoints");
    }
    private static int vaultCount(String prefix) throws IOException {
        Path dir = ChunkVaultStorage.getVaultDir(); if (!Files.isDirectory(dir)) return 0;
        int count = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().startsWith(prefix))::iterator) {
                ListNBT items = CompressedStreamTools.readCompressed(file.toFile()).getList("Items", 10);
                for (int i = 0; i < items.size(); i++) count += ItemStack.of(items.getCompound(i)).getCount();
            }
        }
        return count;
    }
    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static void write(Path path, String content) throws IOException { Files.createDirectories(path.getParent()); Files.write(path, content.getBytes(StandardCharsets.UTF_8)); }
    private static String side() { return restart() ? "restart" : "server"; }
    private static void pass(String marker) { status(side(), "PASS " + marker); }
    private static void finish() {
        state = 99;
        if (unlisten != null) unlisten.run();
        status(side(), "FINISHED policy-network-server"); server.halt(false);
    }
}
