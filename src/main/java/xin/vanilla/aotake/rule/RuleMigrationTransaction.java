package xin.vanilla.aotake.rule;

import com.google.gson.*;
import xin.vanilla.aotake.api.rule.EntityRule;
import xin.vanilla.aotake.rule.migration.*;
import xin.vanilla.banira.api.script.*;
import xin.vanilla.banira.common.config.*;
import xin.vanilla.banira.common.util.JsonUtils;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Recoverable local-file transaction. It never publishes scripts or activates cleanup. */
public final class RuleMigrationTransaction {
    public static final List<String> LISTS = Collections.unmodifiableList(Arrays.asList("base.sweep.entityList", "base.chunk.chunkCheckEntityList", "base.sweep.entityRedlist", "base.entityCatch.catchEntity"));
    public static final List<String> MODES = Collections.unmodifiableList(Arrays.asList("base.sweep.entityListMode", "base.chunk.chunkCheckEntityListMode"));
    public static final Set<String> PATHS;
    static { Set<String> paths = new LinkedHashSet<>(LISTS); paths.addAll(MODES); PATHS = Collections.unmodifiableSet(paths); }
    public enum Checkpoint { BEFORE_BACKUP, AFTER_BACKUP, AFTER_STAGING, AFTER_PREPARED, AFTER_INSTALL, AFTER_CONFIG, BEFORE_COMMITTED }
    public enum RecoveryState { NOT_NEEDED, READY, BLOCKED }
    public static final class RecoveryResult {
        private final RecoveryState state; private final String diagnostic;
        RecoveryResult(RecoveryState state, String diagnostic) { this.state = state; this.diagnostic = diagnostic; }
        public RecoveryState state() { return state; }
        public String diagnostic() { return diagnostic; }
    }
    private final RuleFileStore store;
    private final ConfigHolder holder;
    private final ScriptSession<EntityRule> session;
    private final Consumer<RuleBindingPlan> validateBindings;
    private final Consumer<Checkpoint> checkpoint;
    private long sequence;

    public RuleMigrationTransaction(RuleFileStore store, ConfigHolder holder, ScriptSession<EntityRule> session, Consumer<RuleBindingPlan> validateBindings) {
        this(store, holder, session, validateBindings, ignored -> {});
    }
    RuleMigrationTransaction(RuleFileStore store, ConfigHolder holder, ScriptSession<EntityRule> session,
                             Consumer<RuleBindingPlan> validateBindings, Consumer<Checkpoint> checkpoint) {
        this.store = Objects.requireNonNull(store); this.holder = Objects.requireNonNull(holder); this.session = Objects.requireNonNull(session);
        this.validateBindings = Objects.requireNonNull(validateBindings); this.checkpoint = Objects.requireNonNull(checkpoint);
    }

    public synchronized CompletableFuture<PreparedMigration> prepare(ConfigEditSnapshot snapshot) {
        return prepare(snapshot, false);
    }

    synchronized CompletableFuture<PreparedMigration> prepare(ConfigEditSnapshot snapshot, boolean installBuiltins) {
        long ticket = ++sequence;
        try {
            RecoveryResult recovery = recover();
            if (recovery.state == RecoveryState.BLOCKED) throw new IOException(recovery.diagnostic);
            if (installBuiltins) BuiltinRules.install(store);
            if (!snapshot.getValues().keySet().equals(PATHS)) throw new IllegalArgumentException("Snapshot must include four lists and their modes");
            if (snapshot.getSourceBytes().length > RuleFileStore.MAX_CONFIG_BYTES) throw new IOException("Config backup exceeds size limit");
            RuleCatalog existing = store.loadCatalog(); Map<String,RuleSource> additions = new LinkedHashMap<>();
            Map<String,Object> changes = new LinkedHashMap<>(); RuleBindingPlan known = knownBindings();
            JsonArray diagnostics = new JsonArray();
            for (String path : LISTS) {
                Object value = snapshot.getValues().get(path);
                if (!(value instanceof List)) throw new IllegalArgumentException("Expected rule list: " + path);
                List<String> migrated = new ArrayList<>(); int index = 0;
                for (Object item : (List<?>) value) {
                    if (!(item instanceof String)) throw new IllegalArgumentException("Expected string at " + path + "[" + index + "]");
                    String rule = (String) item;
                    try {
                        if (RuleCatalog.isResource(rule)) migrated.add(rule);
                        else if (rule.startsWith("script:")) { existing.validateReferences(Collections.singletonList(rule)); migrated.add(rule); }
                        else {
                            RuleAst ast = LegacyRuleParser.parse(rule.trim());
                            String id = "legacy_" + RuleFileStore.hash(ast.source);
                            RuleSource previous = existing.sources().get(id);
                            if (previous == null && !additions.containsKey(id)) {
                                RuleSource source = JavaRuleEmitter.emit(id, ast, RuleTypeResolver.resolve(ast, known)); additions.put(id, source);
                                JsonObject diagnostic = new JsonObject(); diagnostic.addProperty("id", id);
                                diagnostic.add("adjustments", JsonUtils.GSON.toJsonTree(source.adjustments())); diagnostics.add(diagnostic);
                            } else if (previous != null && !previous.sourceHash().equals(RuleFileStore.hash(ast.source))) throw new IllegalArgumentException("Rule ID collision: " + id);
                            migrated.add("script:" + id);
                        }
                    } catch (RuntimeException error) { throw new IllegalArgumentException(path + "[" + index + "]: " + error.getMessage(), error); }
                    index++;
                }
                changes.put(path, Collections.unmodifiableList(migrated));
            }
            RuleCatalog catalog = RuleCatalog.extend(existing, additions);
            boolean migration = false; for (String path : LISTS) migration |= !changes.get(path).equals(snapshot.getValues().get(path));
            String id = UUID.randomUUID().toString();
            if (migration) {
                checkpoint.accept(Checkpoint.BEFORE_BACKUP);
                String backup = "backups/rule-migration/" + id + "/";
                store.writeNew(backup + "original.config", snapshot.getSourceBytes());
                JsonObject report = RuleFileStore.versioned(); report.addProperty("configFile", snapshot.getFileName()); report.addProperty("sourceRevision", snapshot.getRevision());
                report.add("originalValues", JsonUtils.GSON.toJsonTree(snapshot.getValues())); report.add("targetValues", targetValues(snapshot, changes)); report.add("adjustments", diagnostics);
                JsonObject hashes = new JsonObject(); catalog.files().forEach((path, bytes) -> hashes.addProperty(path, RuleFileStore.hash(bytes)));
                report.add("fileHashes", hashes); report.addProperty("targetManifestHash", RuleFileStore.hash(catalog.manifestBytes()));
                store.writeNew(backup + "report.json", RuleFileStore.jsonBytes(report));
                checkpoint.accept(Checkpoint.AFTER_BACKUP);
                for (Map.Entry<String,byte[]> file : catalog.files().entrySet()) store.writeNew(stage(id, file.getKey()), file.getValue());
                store.writeNew(stage(id, RuleFileStore.MANIFEST), catalog.manifestBytes());
                checkpoint.accept(Checkpoint.AFTER_STAGING);
            }
            // Only compilation is asynchronous; the supplied owner executor performs instantiation.
            for (RuleSource source : catalog.sources().values()) validateBindings.accept(source.bindings());
            List<ScriptSource> scripts = new ArrayList<>(); catalog.sources().values().forEach(source -> scripts.add(source.script()));
            final boolean needsMigration = migration;
            return session.prepare(scripts).thenApply(compiled -> new PreparedMigration(this, ticket, id, snapshot, changes, catalog, compiled, needsMigration));
        } catch (Exception error) {
            CompletableFuture<PreparedMigration> failure = new CompletableFuture<>(); failure.completeExceptionally(error); return failure;
        }
    }
    private RuleBindingPlan knownBindings() throws IOException {
        byte[] types = store.optional("rules/types.json", RuleFileStore.MAX_SOURCE_BYTES);
        RuleBindingPlan known = RuleBindingPlan.defaults();
        if (types != null) for (RuleBindingPlan.Binding binding : RuleCatalog.decodeBindings(RuleFileStore.object(types)).slots()) {
            if (binding.kind().equals("builtin")) throw new IOException("Built-in type overrides are not allowed");
            known = known.withBinding(binding.kind(), binding.payload(), binding.type(), binding.nullable());
        }
        return known;
    }

    public synchronized ConfigCommitResult commit(PreparedMigration prepared) throws IOException {
        if (prepared.owner != this) throw new IllegalArgumentException("Candidate belongs to another migration transaction");
        if (prepared.ticket != sequence || prepared.consumed) throw new IllegalStateException("Stale or consumed migration candidate");
        prepared.consumed = true;
        if (!prepared.migration) {
            verifyCatalog(prepared.catalog);
            ConfigEditSnapshot current = holder.snapshotForEdit(PATHS);
            return current.getRevision().equals(prepared.snapshot.getRevision()) ? ConfigCommitResult.UNCHANGED : ConfigCommitResult.CONFLICT;
        }
        JsonObject journal = RuleFileStore.versioned(); journal.addProperty("transaction", prepared.id); journal.addProperty("state", "PREPARED");
        journal.addProperty("configFile", prepared.snapshot.getFileName()); journal.addProperty("sourceRevision", prepared.snapshot.getRevision());
        journal.add("targetValues", targetValues(prepared.snapshot, prepared.changes));
        journal.addProperty("oldManifest", prepared.catalog.originalManifest() == null ? "" : RuleFileStore.hash(prepared.catalog.originalManifest()));
        journal.addProperty("newManifest", RuleFileStore.hash(prepared.catalog.manifestBytes()));
        JsonObject files = new JsonObject(); prepared.catalog.files().forEach((path, bytes) -> files.addProperty(path, RuleFileStore.hash(bytes))); journal.add("files", files);
        JsonObject existingFiles = new JsonObject();
        if (prepared.catalog.originalManifest() != null) {
            for (Map.Entry<String,JsonElement> entry : RuleFileStore.object(prepared.catalog.originalManifest()).getAsJsonObject("rules").entrySet()) {
                existingFiles.addProperty(RuleCatalog.sourcePath(entry.getKey()), true);
                existingFiles.addProperty(RuleCatalog.bindingPath(entry.getKey()), true);
            }
        }
        journal.add("existingFiles", existingFiles);
        byte[] previous = store.optional(RuleFileStore.JOURNAL, RuleFileStore.MAX_METADATA_BYTES);
        if (previous != null && !terminal(RuleFileStore.object(previous))) throw new IOException("Unresolved migration journal");
        store.replace(RuleFileStore.JOURNAL, previous, RuleFileStore.jsonBytes(journal));
        checkpoint.accept(Checkpoint.AFTER_PREPARED);
        verifyStaged(journal);
        for (Map.Entry<String,byte[]> file : prepared.catalog.files().entrySet()) store.writeNew(file.getKey(), file.getValue());
        store.replace(RuleFileStore.MANIFEST, prepared.catalog.originalManifest(), prepared.catalog.manifestBytes());
        checkpoint.accept(Checkpoint.AFTER_INSTALL);
        prepared.catalog.verifyFiles(store);
        ConfigCommitResult result = holder.compareAndSetAll(prepared.snapshot, prepared.changes, ConfigEditOrigin.LOCAL_MIGRATION);
        if (result == ConfigCommitResult.CONFLICT) return result;
        checkpoint.accept(Checkpoint.AFTER_CONFIG);
        ConfigEditSnapshot actual = holder.snapshotForEdit(PATHS);
        if (!targetValues(prepared.snapshot, prepared.changes).equals(JsonUtils.GSON.toJsonTree(actual.getValues()))) throw new IOException("Configuration changed during migration commit");
        prepared.catalog.verifyFiles(store);
        checkpoint.accept(Checkpoint.BEFORE_COMMITTED);
        finish(journal, "COMMITTED");
        return result;
    }
    private void verifyCatalog(RuleCatalog catalog) throws IOException {
        if (catalog.originalManifest() == null && catalog.sources().isEmpty()) {
            if (store.optional(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES) != null) throw new IOException("Rule catalog created during preparation");
        } else catalog.verifyFiles(store);
    }

    /** Recovery never restores a backup over third-party edits and never publishes executable code. */
    public synchronized RecoveryResult recover() {
        try {
            byte[] bytes = store.optional(RuleFileStore.JOURNAL, RuleFileStore.MAX_METADATA_BYTES);
            if (bytes == null) return new RecoveryResult(RecoveryState.NOT_NEEDED, "");
            JsonObject journal = RuleFileStore.object(bytes); RuleFileStore.requireVersion(journal);
            if (terminal(journal)) return new RecoveryResult(RecoveryState.NOT_NEEDED, "");
            if (!"PREPARED".equals(journal.get("state").getAsString())) throw new IOException("Unknown migration state");
            String id = transactionId(journal);
            byte[] backup = store.read("backups/rule-migration/" + id + "/original.config", RuleFileStore.MAX_CONFIG_BYTES);
            if (!RuleFileStore.hash(backup).equals(journal.get("sourceRevision").getAsString())) throw new IOException("Migration backup differs");
            verifyStaged(journal);
            ConfigEditSnapshot current = holder.snapshotForEdit(PATHS);
            if (!current.getFileName().equals(journal.get("configFile").getAsString())) throw new IOException("Migration belongs to another config file");
            boolean original = current.getRevision().equals(journal.get("sourceRevision").getAsString());
            boolean target = JsonUtils.GSON.toJsonTree(current.getValues()).equals(journal.get("targetValues"));
            if (!original && !target) throw new IOException("Configuration differs from both migration revisions; retain files and backup for repair");
            byte[] manifest = store.optional(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES);
            String hash = manifest == null ? "" : RuleFileStore.hash(manifest);
            boolean oldManifest = hash.equals(journal.get("oldManifest").getAsString());
            if (!hash.equals(journal.get("newManifest").getAsString()) && !(original && oldManifest)) throw new IOException("Rule manifest changed during migration");
            for (Map.Entry<String,JsonElement> entry : journal.getAsJsonObject("files").entrySet()) {
                requireArtifactPath(entry.getKey());
                byte[] file = store.optional(entry.getKey(), RuleFileStore.MAX_SOURCE_BYTES);
                if (file == null && original && oldManifest && !journal.getAsJsonObject("existingFiles").has(entry.getKey())) continue;
                if (file == null || !RuleFileStore.hash(file).equals(entry.getValue().getAsString())) throw new IOException("Rule file changed or missing: " + entry.getKey());
            }
            finish(journal, original ? "ABORTED" : "COMMITTED");
            return new RecoveryResult(RecoveryState.READY, original ? "Original config retained; prepare again" : "Committed references recovered; compile before activation");
        } catch (Exception error) { return new RecoveryResult(RecoveryState.BLOCKED, error.getMessage()); }
    }
    private void verifyStaged(JsonObject journal) throws IOException {
        String id = transactionId(journal); JsonObject files = journal.getAsJsonObject("files");
        if (files == null || files.size() > RuleFileStore.MAX_SCRIPTS * 2) throw new IOException("Invalid staged file count");
        for (Map.Entry<String,JsonElement> entry : files.entrySet()) {
            requireArtifactPath(entry.getKey());
            if (!RuleFileStore.hash(store.read(stage(id, entry.getKey()), RuleFileStore.MAX_SOURCE_BYTES)).equals(entry.getValue().getAsString())) throw new IOException("Staged rule file changed: " + entry.getKey());
        }
        if (!RuleFileStore.hash(store.read(stage(id, RuleFileStore.MANIFEST), RuleFileStore.MAX_METADATA_BYTES)).equals(journal.get("newManifest").getAsString())) throw new IOException("Staged manifest changed");
    }
    private void finish(JsonObject journal, String state) throws IOException {
        byte[] expected = RuleFileStore.jsonBytes(journal); journal.addProperty("state", state);
        store.replace(RuleFileStore.JOURNAL, expected, RuleFileStore.jsonBytes(journal));
    }
    private static boolean terminal(JsonObject journal) { String state = journal.get("state").getAsString(); return "COMMITTED".equals(state) || "ABORTED".equals(state); }
    private static String transactionId(JsonObject journal) throws IOException {
        String id = journal.get("transaction").getAsString();
        if (!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw new IOException("Invalid transaction id");
        return id;
    }
    private static void requireArtifactPath(String path) throws IOException {
        if (!path.matches("rules/(sources/[a-z0-9][a-z0-9_-]{0,79}\\.java|bindings/[a-z0-9][a-z0-9_-]{0,79}\\.json)")) throw new IOException("Invalid migration artifact path");
    }
    private static String stage(String id, String path) { return "rules/.staging/" + id + "/" + path; }
    private static JsonObject targetValues(ConfigEditSnapshot snapshot, Map<String,Object> changes) {
        Map<String,Object> values = new LinkedHashMap<>(snapshot.getValues()); values.putAll(changes); return JsonUtils.GSON.toJsonTree(values).getAsJsonObject();
    }
    public static final class PreparedMigration {
        private final RuleMigrationTransaction owner; private final long ticket; private final String id;
        private final ConfigEditSnapshot snapshot; private final Map<String,Object> changes;
        private final RuleCatalog catalog; private final PreparedScripts<EntityRule> compiled; private final boolean migration;
        private boolean consumed;
        PreparedMigration(RuleMigrationTransaction owner, long ticket, String id, ConfigEditSnapshot snapshot, Map<String,Object> changes,
                          RuleCatalog catalog, PreparedScripts<EntityRule> compiled, boolean migration) {
            this.owner = owner; this.ticket = ticket; this.id = id; this.snapshot = snapshot; this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(changes));
            this.catalog = catalog; this.compiled = compiled; this.migration = migration;
        }
        public String id() { return id; }
        public RuleCatalog catalog() { return catalog; }
        public PreparedScripts<EntityRule> compiled() { return compiled; }
        public Map<String,Object> changes() { return changes; }
        public ConfigEditSnapshot snapshot() { return snapshot; }
    }
}
