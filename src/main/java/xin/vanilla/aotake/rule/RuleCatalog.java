package xin.vanilla.aotake.rule;

import com.google.gson.*;
import xin.vanilla.banira.api.script.ScriptSource;
import xin.vanilla.aotake.api.rule.*;
import java.util.function.BiFunction;
import xin.vanilla.banira.common.config.ConfigEditGuard;
import xin.vanilla.banira.common.util.JsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Immutable local source snapshot. Only a successfully prepared catalog may be installed as an edit guard. */
public final class RuleCatalog {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,79}");
    private static final Pattern RESOURCE = Pattern.compile("(?:[a-z0-9_.-]+|\\*):(?:[a-z0-9_./-]+|\\*)");
    private final Map<String,RuleSource> sources;
    private final Map<String,byte[]> files;
    private final JsonObject manifest;
    private final byte[] originalManifest;

    RuleCatalog(Map<String,RuleSource> sources, Map<String,byte[]> files, JsonObject manifest, byte[] originalManifest) {
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        Map<String,byte[]> copy = new LinkedHashMap<>(); files.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        this.files = Collections.unmodifiableMap(copy); this.manifest = copyJson(manifest);
        this.originalManifest = originalManifest == null ? null : originalManifest.clone();
    }
    public Map<String,RuleSource> sources() { return sources; }
    Map<String, NativeMembers> prepareMembers(Map<String, EntityRule> compiled,
                                            BiFunction<List<NativeMemberSpec>, ClassLoader, NativeMembers> binder) throws IOException {
        Map<String, NativeMembers> result = new LinkedHashMap<>();
        for (String id : sources.keySet()) {
            JsonObject metadata = manifest.getAsJsonObject("rules").getAsJsonObject(id);
            NativeMemberDeclarations declarations = NativeMemberDeclarations.read(metadata.has("nativeMembers") ? metadata.get("nativeMembers") : new JsonArray());
            result.put(id, declarations.prepare(Objects.requireNonNull(compiled.get(id), "Compiled rule missing: " + id).getClass().getClassLoader(), binder));
        }
        return Collections.unmodifiableMap(result);
    }
    public static boolean isResource(String rule) { return rule != null && RESOURCE.matcher(rule).matches() && !rule.startsWith("script:"); }
    public static String requireId(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid script id: " + id);
        return id;
    }
    public void validateReferences(List<String> rules) {
        for (Object item : rules) {
            if (!(item instanceof String)) throw new IllegalArgumentException("Rule entries must be strings");
            String rule = (String) item;
            if (isResource(rule)) continue;
            if (!rule.startsWith("script:") || !sources.containsKey(requireId(rule.substring(7)))) {
                throw new IllegalArgumentException("Only resource rules or locally approved script IDs are allowed: " + rule);
            }
        }
    }
    public ConfigEditGuard guard() {
        return (origin, proposed) -> {
            for (String path : RuleMigrationTransaction.LISTS) if (proposed.containsKey(path)) {
                Object value = proposed.get(path);
                if (!(value instanceof List)) throw new IllegalArgumentException("Expected rule list: " + path);
                @SuppressWarnings("unchecked") List<String> rules = (List<String>) value;
                validateReferences(rules);
            }
        };
    }
    byte[] manifestBytes() {
        if (originalManifest != null && manifest.equals(JsonUtils.GSON.fromJson(new String(originalManifest, StandardCharsets.UTF_8), JsonObject.class))) return originalManifest.clone();
        return RuleFileStore.jsonBytes(manifest);
    }
    byte[] originalManifest() { return originalManifest == null ? null : originalManifest.clone(); }
    Map<String,byte[]> files() { Map<String,byte[]> copy = new LinkedHashMap<>(); files.forEach((path, bytes) -> copy.put(path, bytes.clone())); return copy; }
    JsonObject manifest() { return copyJson(manifest); }
    private static JsonObject copyJson(JsonObject json) { return JsonUtils.GSON.fromJson(json.toString(), JsonObject.class); }
    public void verifyFiles(RuleFileStore store) throws IOException {
        for (Map.Entry<String,byte[]> entry : files.entrySet()) {
            if (!Arrays.equals(entry.getValue(), store.read(entry.getKey(), RuleFileStore.MAX_SOURCE_BYTES))) throw new IOException("Rule changed after preparation: " + entry.getKey());
        }
        if (!Arrays.equals(manifestBytes(), store.read(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES))) throw new IOException("Rule manifest changed after preparation");
    }
    static RuleCatalog load(RuleFileStore store) throws IOException {
        byte[] original = store.optional(RuleFileStore.MANIFEST, RuleFileStore.MAX_METADATA_BYTES);
        JsonObject manifest = original == null ? RuleFileStore.versioned() : RuleFileStore.object(original);
        if (original == null) manifest.add("rules", new JsonObject());
        RuleFileStore.requireVersion(manifest);
        Map<String,RuleSource> sources = new LinkedHashMap<>(); Map<String,byte[]> files = new LinkedHashMap<>();
        JsonObject rules = manifest.getAsJsonObject("rules");
        if (rules == null || rules.size() > RuleFileStore.MAX_SCRIPTS) throw new IOException("Invalid rule catalog size");
        long size = 0;
        try {
            for (Map.Entry<String,JsonElement> entry : rules.entrySet()) {
                String id = requireId(entry.getKey()); JsonObject info = entry.getValue().getAsJsonObject();
                byte[] java = store.read(sourcePath(id), RuleFileStore.MAX_SOURCE_BYTES), binding = store.read(bindingPath(id), RuleFileStore.MAX_SOURCE_BYTES);
                size += java.length + binding.length; if (size > RuleFileStore.MAX_CONFIG_BYTES) throw new IOException("Rule catalog exceeds size limit");
                RuleSource source = new RuleSource(new ScriptSource(id, info.get("className").getAsString(), id + ".java", RuleFileStore.utf8(java)),
                        decodeBindings(RuleFileStore.object(binding)), info.get("sourceHash").getAsString(), Collections.emptyList());
                if (!source.sourceHash().matches("[a-f0-9]{64}")) throw new IOException("Invalid original rule hash: " + id);
                sources.put(id, source); files.put(sourcePath(id), java); files.put(bindingPath(id), binding);
            }
        } catch (RuntimeException error) { throw new IOException("Invalid rule catalog", error); }
        return new RuleCatalog(sources, files, manifest, original);
    }
    static String sourcePath(String id) { return "rules/sources/" + requireId(id) + ".java"; }
    static String bindingPath(String id) { return "rules/bindings/" + requireId(id) + ".json"; }
    static JsonObject encodeBindings(RuleBindingPlan plan) {
        JsonObject json = RuleFileStore.versioned(); JsonArray slots = new JsonArray();
        for (RuleBindingPlan.Binding binding : plan.slots()) {
            JsonObject slot = new JsonObject(); slot.addProperty("name", binding.name()); slot.addProperty("kind", binding.kind());
            slot.addProperty("payload", binding.payload()); slot.addProperty("type", binding.type().getName());
            slot.addProperty("nullable", binding.nullable()); slots.add(slot);
        }
        json.add("slots", slots); return json;
    }
    static RuleBindingPlan decodeBindings(JsonObject json) throws IOException {
        RuleFileStore.requireVersion(json); List<RuleBindingPlan.Binding> bindings = new ArrayList<>();
        try {
            JsonArray slots = json.getAsJsonArray("slots");
            if (slots == null || slots.size() > 128) throw new IOException("Invalid binding count");
            Set<String> names = new HashSet<>();
            for (JsonElement element : slots) {
                JsonObject slot = element.getAsJsonObject(); String name = slot.get("name").getAsString(), kind = slot.get("kind").getAsString();
                if (!names.add(name) || !name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || !Arrays.asList("builtin", "nbt", "field", "synced").contains(kind)) throw new IOException("Invalid binding: " + name);
                bindings.add(new RuleBindingPlan.Binding(name, kind, slot.get("payload").getAsString(), bindingType(slot.get("type").getAsString()), slot.get("nullable").getAsBoolean(), -1));
            }
        } catch (RuntimeException error) { throw new IOException("Invalid binding metadata", error); }
        return RuleBindingPlan.defaults().resolved(bindings);
    }
    private static Class<?> bindingType(String name) throws IOException {
        for (Class<?> type : new Class<?>[]{String.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
                Character.class, Class.class, Collection.class, List.class, Set.class, Map.class, String[].class, Object[].class,
                int[].class, long[].class, double[].class, float[].class, byte[].class, short[].class, boolean[].class, char[].class}) {
            if (type.getName().equals(name)) return type;
        }
        throw new IOException("Unsupported binding type: " + name);
    }
    static RuleCatalog extend(RuleCatalog existing, Map<String,RuleSource> additions) throws IOException {
        Map<String,RuleSource> sources = new LinkedHashMap<>(existing.sources); sources.putAll(additions);
        if (sources.size() > RuleFileStore.MAX_SCRIPTS) throw new IOException("Too many scripts");
        Map<String,byte[]> files = existing.files(); JsonObject manifest = existing.manifest();
        for (Map.Entry<String,RuleSource> entry : additions.entrySet()) {
            String id = entry.getKey(); RuleSource source = entry.getValue();
            byte[] java = source.script().getSource().getBytes(StandardCharsets.UTF_8), bindings = RuleFileStore.jsonBytes(encodeBindings(source.bindings()));
            if (java.length > RuleFileStore.MAX_SOURCE_BYTES || bindings.length > RuleFileStore.MAX_SOURCE_BYTES) throw new IOException("Generated rule exceeds file limit: " + id);
            files.put(sourcePath(id), java); files.put(bindingPath(id), bindings);
            JsonObject info = new JsonObject(); info.addProperty("className", source.script().getClassName()); info.addProperty("sourceHash", source.sourceHash());
            manifest.getAsJsonObject("rules").add(id, info);
        }
        long total = 0; for (byte[] bytes : files.values()) total += bytes.length;
        if (total > RuleFileStore.MAX_CONFIG_BYTES) throw new IOException("Generated rules exceed total limit");
        return new RuleCatalog(sources, files, manifest, existing.originalManifest);
    }
}
