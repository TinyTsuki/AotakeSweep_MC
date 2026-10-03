package xin.vanilla.aotake.rule;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import xin.vanilla.banira.api.script.ScriptSourceGroup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.*;

/**
 * Local trusted code only. A malformed or edited catalog is never silently regenerated.
 */
public final class SweepPolicyFileStore {
    public static final String MANIFEST = "rules/policies/manifest.json";
    static final String SOURCES = "rules/policies/sources/";
    static final String NAMESPACE = "xin.vanilla.banira.generated.";
    private final RuleFileStore files;

    public SweepPolicyFileStore(RuleFileStore files) {
        this.files = Objects.requireNonNull(files);
    }

    public SweepPolicyCatalog load() throws IOException {
        initialize();
        byte[] manifest = files.read(MANIFEST, RuleFileStore.MAX_METADATA_BYTES);
        JsonObject root = parse(manifest);
        keys(root, "policySchema", "apiVersion", "policies");
        if (!root.get("policySchema").isJsonPrimitive() || !root.getAsJsonPrimitive("policySchema").isNumber()
                || !"1".equals(root.get("policySchema").getAsString()) || !"1".equals(string(root, "apiVersion")))
            throw new IOException("Unsupported sweep policy metadata version");
        JsonArray entries = array(root, "policies");
        if (entries.size() > RuleFileStore.MAX_SCRIPTS) throw new IOException("Too many policies");
        Map<String, ScriptSourceGroup> groups = new LinkedHashMap<>();
        Map<String, NativeMemberDeclarations> bindings = new LinkedHashMap<>();
        Map<String, byte[]> snapshot = new LinkedHashMap<>();
        snapshot.put(MANIFEST, manifest);
        int count = 0;
        long bytes = manifest.length;
        try {
            for (JsonElement entry : entries) {
                JsonObject policy = object(entry);
                keys(policy, "id", "entryClassName", "sourceFiles", "nativeMembers");
                String id = RuleCatalog.requireId(string(policy, "id"));
                if (groups.containsKey(id)) throw new IOException("Duplicate policy ID: " + id);
                String entryClass = className(string(policy, "entryClassName"));
                if (!entryClass.startsWith(NAMESPACE))
                    throw new IOException("Policy entrypoint must use the script namespace");
                Map<String, String> sources = new LinkedHashMap<>();
                for (JsonElement item : array(policy, "sourceFiles")) {
                    String name = string(item);
                    if (!name.endsWith(".java") || sources.containsKey(name))
                        throw new IOException("Invalid or duplicate source file: " + name);
                    byte[] source = files.read(SOURCES + name, RuleFileStore.MAX_SOURCE_BYTES);
                    if (++count > RuleFileStore.MAX_SCRIPTS || (bytes += source.length) > RuleFileStore.MAX_CONFIG_BYTES)
                        throw new IOException("Policy sources exceed total limits");
                    sources.put(name, RuleFileStore.utf8(source));
                    snapshot.put(SOURCES + name, source);
                }
                String simple = entryClass.substring(entryClass.lastIndexOf('.') + 1) + ".java";
                if (sources.keySet().stream().noneMatch(name -> name.equals(simple) || name.endsWith("/" + simple)))
                    throw new IOException("Missing policy entrypoint source: " + simple);
                NativeMemberDeclarations members = NativeMemberDeclarations.read(policy.get("nativeMembers"));
                groups.put(id, new ScriptSourceGroup(id, entryClass, sources));
                bindings.put(id, members);
            }
        } catch (RuntimeException error) {
            throw new IOException("Invalid sweep policy catalog", error);
        }
        return new SweepPolicyCatalog(files, groups, bindings, snapshot);
    }

    private void initialize() throws IOException {
        if (files.optional(MANIFEST, RuleFileStore.MAX_METADATA_BYTES) != null) return;
        String source = SOURCES + "CustomSweepPolicy.java";
        if (files.optional(source, RuleFileStore.MAX_SOURCE_BYTES) == null) {
            try (InputStream input = SweepPolicyFileStore.class.getResourceAsStream("/rules/policies/CustomSweepPolicy.java.template")) {
                if (input == null) throw new IOException("Missing built-in sweep policy template");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    if (out.size() + n > RuleFileStore.MAX_SOURCE_BYTES)
                        throw new IOException("Policy template too large");
                    out.write(buffer, 0, n);
                }
                files.writeNew(source, out.toByteArray());
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("policySchema", 1);
        root.addProperty("apiVersion", "1");
        JsonObject custom = new JsonObject();
        custom.addProperty("id", "custom");
        custom.addProperty("entryClassName", NAMESPACE + "policies.CustomSweepPolicy");
        JsonArray sources = new JsonArray();
        sources.add("CustomSweepPolicy.java");
        custom.add("sourceFiles", sources);
        custom.add("nativeMembers", new JsonArray());
        JsonArray policies = new JsonArray();
        policies.add(custom);
        root.add("policies", policies);
        files.writeNew(MANIFEST, RuleFileStore.jsonBytes(root));
    }

    static JsonObject parse(byte[] bytes) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(RuleFileStore.utf8(bytes)))) {
            reader.setLenient(false);
            JsonObject root = object(read(reader, 0));
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing policy metadata");
            return root;
        } catch (RuntimeException error) {
            throw new IOException("Invalid sweep policy JSON", error);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > 16) throw new IOException("Policy metadata nesting exceeds limit");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate metadata field: " + name);
                    object.add(name, read(reader, depth + 1));
                }
                reader.endObject();
                return object;
            case BEGIN_ARRAY:
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= 128) throw new IOException("Metadata array exceeds limit");
                    array.add(read(reader, depth + 1));
                }
                reader.endArray();
                return array;
            case STRING:
                return new JsonPrimitive(reader.nextString());
            case NUMBER:
                return new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull();
                return JsonNull.INSTANCE;
            default:
                throw new IOException("Unexpected metadata token");
        }
    }

    private static JsonObject object(JsonElement e) throws IOException {
        if (!e.isJsonObject()) throw new IOException("Expected metadata object");
        return e.getAsJsonObject();
    }

    private static JsonArray array(JsonObject o, String key) throws IOException {
        if (!o.has(key) || !o.get(key).isJsonArray()) throw new IOException("Expected array: " + key);
        return o.getAsJsonArray(key);
    }

    private static String string(JsonObject o, String key) throws IOException {
        if (!o.has(key)) throw new IOException("Missing field: " + key);
        return string(o.get(key));
    }

    private static String string(JsonElement e) throws IOException {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isEmpty())
            throw new IOException("Expected nonempty string");
        return e.getAsString();
    }

    private static String className(String value) throws IOException {
        if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))
            throw new IOException("Invalid Java type name: " + value);
        return value;
    }

    private static void keys(JsonObject object, String... expected) throws IOException {
        Set<String> actual = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) actual.add(entry.getKey());
        if (!actual.equals(new HashSet<>(Arrays.asList(expected))))
            throw new IOException("Unexpected or missing policy fields: " + actual);
    }
}
