package xin.vanilla.aotake.internal.server.dev;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.SweepResult;
import xin.vanilla.aotake.data.world.ChunkVaultStorage;
import xin.vanilla.aotake.internal.server.AotakeRuleRuntime;
import xin.vanilla.aotake.rule.*;
import xin.vanilla.aotake.util.EntitySweeper;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.api.BaniraServer;
import xin.vanilla.banira.common.config.BaniraConfig;
import xin.vanilla.banira.common.config.ConfigHolder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static xin.vanilla.aotake.internal.dev.AotakeRuleNetworkProbe.*;

public final class AotakeRuleNetworkSmokeServerRunner {
    private static int state, ticks, deadlineTick;
    private static long start, originalGeneration;
    private static MinecraftServer server;
    private static ConfigHolder holder;
    private static Path source;
    private static String originalSource;
    private static CompletableFuture<Long> pending;
    private static final List<Integer> savedValues = new ArrayList<>();
    private static final List<ItemEntity> drops = new ArrayList<>();
    private static final EntitySweeper sweeper = new EntitySweeper();
    private static SweepResult result;
    private static Runnable unregister;

    private AotakeRuleNetworkSmokeServerRunner() {
    }

    public static void register() {
        if (!enabled()) return;
        MinecraftForge.EVENT_BUS.addListener(AotakeRuleNetworkSmokeServerRunner::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) -> status(side(), "PASS server-shutdown"));
    }

    private static String side() {
        return restart() ? "restart" : "server";
    }

    private static void pass(String text) {
        status(side(), "PASS " + text);
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == 99) return;
        server = BaniraServer.currentAs(MinecraftServer.class);
        if (server == null || !server.isRunning()) return;
        try {
            if (start == 0) {
                start = System.nanoTime();
                pending = AotakeRuleRuntime.reload();
            }
            require(System.nanoTime() - start < 150_000_000_000L, "Server rule smoke timed out in state " + state);
            ticks++;
            switch (state) {
                case 0:
                    if (!pending.isDone()) return;
                    originalGeneration = pending.join();
                    holder = BaniraConfig.holder(CommonConfig.class);
                    if (restart()) {
                        verifyRestart();
                        finish();
                        return;
                    }
                    for (String path : RuleMigrationTransaction.LISTS)
                        require(((List<?>) holder.get(path)).get(0).toString().startsWith("script:legacy_"), "Not migrated: " + path);
                    String reference = ((List<String>) holder.get(RuleMigrationTransaction.LISTS.get(0))).get(0);
                    source = RuleFileStore.local().root().resolve("rules/sources/" + reference.substring(7) + ".java");
                    originalSource = read(source);
                    unregister = holder.onSaved(paths -> savedValues.add(((Number) holder.get(BATCH)).intValue()));
                    pass("four-list-production-migration");
                    state = 1;
                    break;
                case 1:
                    if (server.getPlayerList().getPlayers().isEmpty()) return;
                    server.getPlayerList().op(server.getPlayerList().getPlayers().get(0).getGameProfile());
                    status("server", "READY configuration");
                    state = 2;
                    break;
                case 2:
                    if (((Number) holder.get(BATCH)).intValue() != 23) return;
                    require(!savedValues.contains(999), "Invalid remote edit was partially saved");
                    require(((List<?>) holder.get(RuleMigrationTransaction.LISTS.get(0))).get(0).toString().startsWith("script:legacy_"), "Unreviewed remote rule installed");
                    pass("remote-guard-and-save");
                    state = 3;
                    break;
                case 3:
                    if (!has("client", "FINISHED rule-network-client")) return;
                    originalGeneration = AotakeRuleRuntime.acquire(RuleManager.RuleUse.SWEEP).id();
                    write(source, "invalid Java");
                    pending = AotakeRuleRuntime.reload();
                    state = 4;
                    break;
                case 4:
                    if (!pending.isDone()) return;
                    require(pending.isCompletedExceptionally(), "Bad source compiled");
                    require(AotakeRuleRuntime.isUsable(originalGeneration), "Same-selection compile failure lost last healthy generation");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "WHITE");
                    holder.save();
                    pending = AotakeRuleRuntime.reload();
                    state = 5;
                    break;
                case 5:
                    if (!pending.isDone()) return;
                    require(pending.isCompletedExceptionally() && !AotakeRuleRuntime.isUsable(originalGeneration), "Changed selection reused old generation");
                    pass("compile-failure-retains-matching-generation-only");
                    write(source, originalSource.replaceAll("return .*;", "return context.tick() >= 999999;"));
                    holder.set(RuleMigrationTransaction.MODES.get(0), "BLACK");
                    holder.save();
                    pending = AotakeRuleRuntime.reload();
                    state = 6;
                    break;
                case 6:
                    if (!pending.isDone()) return;
                    pending.join();
                    ItemEntity probe = new ItemEntity(server.overworld(), 0, 80, 0, new ItemStack(Items.APPLE));
                    probe.tickCount = 20;
                    require(!AotakeRuleRuntime.acquire(RuleManager.RuleUse.SWEEP).matches(RuleGeneration.RuleList.SWEEP, probe), "Edited source did not activate");
                    pass("valid-source-edit-activates");
                    write(source, originalSource);
                    pending = AotakeRuleRuntime.reload();
                    state = 7;
                    break;
                case 7:
                    if (!pending.isDone()) return;
                    originalGeneration = pending.join();
                    externalEdit();
                    state = 8;
                    break;
                case 8:
                    if (((Number) holder.get(BATCH)).intValue() != 29) return;
                    holder.save();
                    require(readConfigBatch() == 29, "Save overwrote external edit");
                    pass("external-file-reload-survives-save");
                    write(source, originalSource.replaceAll("return .*;", "throw new IllegalStateException(\"intentional smoke fault\");"));
                    pending = AotakeRuleRuntime.reload();
                    state = 9;
                    break;
                case 9:
                    if (!pending.isDone()) return;
                    long broken = pending.join();
                    ItemEntity subject = new ItemEntity(server.overworld(), 0, 80, 0, new ItemStack(Items.DIAMOND, 3));
                    try {
                        AotakeRuleRuntime.acquire(RuleManager.RuleUse.SWEEP).matches(RuleGeneration.RuleList.SWEEP, subject);
                        throw new AssertionError("Rule fault hidden");
                    } catch (RuleExecutionException expected) {
                    }
                    require(!AotakeRuleRuntime.isUsable(broken) && subject.isAlive() && subject.getItem().getCount() == 3, "Fault mutated entity or left rules usable");
                    pass("runtime-fault-blocks-without-entity-mutation");
                    write(source, originalSource);
                    pending = AotakeRuleRuntime.reload();
                    state = 10;
                    break;
                case 10:
                    if (!pending.isDone()) return;
                    pending.join();
                    startBatches();
                    state = 12;
                    break;
                case 12:
                    if (result.getBatch().get() == 0) return;
                    require(result.getBatch().get() == 1 && vaultCount() == 2, "First batch did not commit exactly one stack");
                    holder.set(RuleMigrationTransaction.MODES.get(0), "WHITE");
                    holder.save();
                    pending = AotakeRuleRuntime.reload();
                    deadlineTick = ticks + 50;
                    state = 11;
                    break;
                case 11:
                    if (!pending.isDone() || ticks < deadlineTick) return;
                    pending.join();
                    EntitySweeper.flushPendingRemovals(server.overworld());
                    require(!drops.get(0).isAlive() && drops.get(1).isAlive() && drops.get(2).isAlive(), "Canceled batch removed incorrect entities");
                    require(result.getBatch().get() == 1 && vaultCount() == 2, "Canceled batch output missing or duplicated");
                    sweeper.clear();
                    require(vaultCount() == 2, "Stopping canceled job lost committed vault output");
                    pass("generation-cancel-keeps-first-batch-vault-and-remaining-entities");
                    Properties checkpoint = new Properties();
                    checkpoint.setProperty("manifest", read(RuleFileStore.local().root().resolve("rules/manifest.json")));
                    checkpoint.setProperty("backups", Long.toString(backupCount()));
                    try (Writer writer = Files.newBufferedWriter(root().resolve("checkpoint.properties"), StandardCharsets.UTF_8)) {
                        checkpoint.store(writer, "Rule smoke");
                    }
                    finish();
                    break;
                default:
                    throw new IllegalStateException("Unexpected state " + state);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            status(side(), "FAIL " + failure);
            finish();
        }
    }

    private static void startBatches() {
        ServerWorld world = server.overworld();
        world.getChunk(0, 0);
        for (int i = 0; i < 3; i++) {
            ItemEntity drop = new ItemEntity(world, 8 + i * 3, 100, 8, new ItemStack(Items.APPLE, 2));
            drop.tickCount = 20;
            drop.setNoGravity(true);
            drop.setPickUpDelay(6000);
            require(world.addFreshEntity(drop), "Cannot spawn probe drop");
            drops.add(drop);
        }
        result = new SweepResult().setChunkOverloadVault(true).setChunkVaultTimePrefix("rule_smoke").setChunkVaultRunId("cancellation");
        sweeper.addDrops(new ArrayList<Entity>(drops), result);
    }

    private static Path configPath() {
        return BaniraDataPaths.gameConfigPath().resolve(holder.getConfigName() + ".toml");
    }

    private static void externalEdit() throws IOException {
        CommentedConfig config;
        try (Reader reader = Files.newBufferedReader(configPath(), StandardCharsets.UTF_8)) {
            config = new TomlParser().parse(reader);
        }
        config.set(BATCH, 29);
        Path temporary = configPath().resolveSibling("rule-smoke-external.tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            new TomlWriter().write(config, writer);
        }
        Files.move(temporary, configPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static int readConfigBatch() throws IOException {
        try (Reader reader = Files.newBufferedReader(configPath(), StandardCharsets.UTF_8)) {
            return ((Number) new TomlParser().parse(reader).get(BATCH)).intValue();
        }
    }

    private static int vaultCount() {
        try (Stream<Path> paths = Files.list(ChunkVaultStorage.getVaultDir())) {
            int count = 0;
            for (Path path : (Iterable<Path>) paths.filter(p -> p.getFileName().toString().startsWith("rule_smoke"))::iterator) {
                ListNBT items = CompressedStreamTools.readCompressed(path.toFile()).getList("Items", 10);
                for (int i = 0; i < items.size(); i++) count += ItemStack.of(items.getCompound(i)).getCount();
            }
            return count;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static long backupCount() throws IOException {
        try (Stream<Path> paths = Files.walk(RuleFileStore.local().root().resolve("backups/rule-migration"))) {
            return paths.filter(p -> p.getFileName().toString().equals("original.config")).count();
        }
    }

    private static void verifyRestart() throws IOException {
        Properties checkpoint = new Properties();
        try (Reader reader = Files.newBufferedReader(root().resolve("checkpoint.properties"), StandardCharsets.UTF_8)) {
            checkpoint.load(reader);
        }
        require(checkpoint.getProperty("manifest").equals(read(RuleFileStore.local().root().resolve("rules/manifest.json"))), "Restart changed catalog");
        require(Long.parseLong(checkpoint.getProperty("backups")) == backupCount(), "Restart repeated migration");
        require(((Number) holder.get(BATCH)).intValue() == 29 && readConfigBatch() == 29, "Restart lost edited config");
        require(vaultCount() == 2, "Restart lost committed vault");
        pass("restart-no-remigration-config-and-vault-persisted");
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void finish() {
        state = 99;
        if (unregister != null) unregister.run();
        sweeper.clear();
        status(side(), "FINISHED rule-network-" + side());
        server.halt(false);
    }
}
