package xin.vanilla.aotake.internal.dev;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.api.BaniraConfigs;
import xin.vanilla.banira.api.BaniraDataPaths;
import xin.vanilla.banira.common.config.ConfigHolder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Read-only config comparison; only the separate dev checkpoint is written. */
public final class AotakeNetworkSmokeConfigs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AotakeNetworkSmokeConfigs() { }

    public static void verify(boolean client) {
        if (!AotakeNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke disabled");
        Class<?> configClass = client ? ClientConfig.class : CommonConfig.class;
        ConfigHolder holder = (ConfigHolder) BaniraConfigs.requireHandle(configClass);
        String phase = AotakeNetworkSmokeStatus.phase();
        try {
            JsonObject snapshot = verify(holder, BaniraDataPaths.gameConfigPath(), phase);
            AotakeNetworkSmokeStatus.append(("phase-one".equals(phase)
                    ? "PASS complete-config-snapshot" : "PASS complete-config-restart")
                    + " fields=" + snapshot.size() + " file=" + holder.getConfigName() + ".toml");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify config " + holder.getConfigName(), error);
        }
    }

    static JsonObject verify(ConfigHolder holder, Path directory, String phase) throws IOException {
        if (!"phase-one".equals(phase) && !"phase-two".equals(phase)) {
            throw new IllegalStateException("Unknown config smoke phase " + phase);
        }
        JsonObject current = new JsonObject();
        for (String path : new TreeSet<>(holder.valuePaths())) {
            Object value = holder.get(path);
            if (value == null) throw new IllegalStateException("Null holder value " + path);
            current.add(path, normalize(value));
        }
        if (current.size() == 0) throw new IllegalStateException("Config holder is empty");
        String name = holder.getConfigName();
        Path toml = directory.resolve(name.endsWith(".toml") ? name : name + ".toml");
        // Parse independently, never load/save the live backend or split serialized list text.
        CommentedConfig disk;
        try (Reader reader = Files.newBufferedReader(toml, StandardCharsets.UTF_8)) {
            disk = new TomlParser().parse(reader);
        }
        for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
            String path = entry.getKey();
            if (!disk.contains(path)) throw new IllegalStateException("Missing TOML value " + path);
            Object value = disk.get(path);
            requireEqual("TOML", path, entry.getValue(), normalize(value));
        }
        Path checkpoint = directory.resolve(name + ".smoke-checkpoint.json");
        if ("phase-one".equals(phase)) {
            try (Writer writer = Files.newBufferedWriter(checkpoint, StandardCharsets.UTF_8)) {
                GSON.toJson(current, writer);
            }
        } else {
            JsonObject saved;
            try (Reader reader = Files.newBufferedReader(checkpoint, StandardCharsets.UTF_8)) {
                saved = GSON.fromJson(reader, JsonObject.class);
            }
            if (saved == null || !paths(current).equals(paths(saved))) {
                throw new IllegalStateException("Checkpoint config paths differ from holder: " + name);
            }
            for (Map.Entry<String, JsonElement> entry : current.entrySet()) {
                requireEqual("checkpoint", entry.getKey(), saved.get(entry.getKey()), entry.getValue());
            }
        }
        return current;
    }

    private static Set<String> paths(JsonObject object) {
        Set<String> paths = new TreeSet<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) paths.add(entry.getKey());
        return paths;
    }

    private static JsonElement normalize(Object value) {
        if (value instanceof Enum<?>) return new JsonPrimitive(((Enum<?>) value).name());
        if (value instanceof Iterable<?>) {
            JsonArray array = new JsonArray();
            for (Object element : (Iterable<?>) value) array.add(normalize(element));
            return array;
        }
        return GSON.toJsonTree(value);
    }

    private static void requireEqual(String source, String path, JsonElement expected, JsonElement actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Config " + source + " mismatch " + path
                    + ": expected=" + expected + " actual=" + actual);
        }
    }
}
