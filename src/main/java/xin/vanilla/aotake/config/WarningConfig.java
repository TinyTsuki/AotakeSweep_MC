package xin.vanilla.aotake.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.util.CollectionUtils;
import xin.vanilla.aotake.util.JsonUtils;
import xin.vanilla.aotake.util.StringUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WarningConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String FILE_NAME = "aotake_sweep-warning.json";


    public static WarningGroupData loadWarningGroups() {
        WarningConfigRaw raw = loadWarningConfigRaw();
        boolean allowLegacy = !raw.configFileExists();
        WarningContentLoadResult contentResult = loadWarningContentGroups(raw, allowLegacy);
        WarningContentLoadResult voiceResult = loadWarningVoiceGroups(raw, allowLegacy);
        List<Map<String, List<String>>> contentGroups = new ArrayList<>(contentResult.groups());
        List<Map<String, List<String>>> voiceGroups = new ArrayList<>(voiceResult.groups());
        if (CollectionUtils.isNullOrEmpty(contentGroups)) {
            contentGroups.add(buildDefaultWarnGroup());
        }
        ensureContentDefaults(contentGroups);
        if (CollectionUtils.isNullOrEmpty(voiceGroups)) {
            voiceGroups.add(buildDefaultVoiceGroup());
        }
        boolean writeFile = contentResult.writeFile() || voiceResult.writeFile() || !raw.configFileExists();
        if (writeFile) {
            saveWarningConfig(buildWarningContentJsonElement(contentGroups), buildWarningContentJsonElement(voiceGroups));
        }
        if (contentResult.clearLegacy()) {
            clearLegacyWarningContent();
        }
        if (voiceResult.clearLegacy()) {
            clearLegacyWarningVoice();
        }
        return new WarningGroupData(contentGroups, voiceGroups);
    }

    public static WarningConfigRaw loadWarningConfigRaw() {
        File file = getWarningConfigPath().toFile();
        boolean configFileExists = file.exists();
        JsonObject root = null;
        if (configFileExists) {
            root = readWarningConfigObject(file);
        } else {
            JsonObject legacyRoot = new JsonObject();
            if (legacyRoot.size() > 0) {
                root = legacyRoot;
            }
        }
        if (root == null) {
            root = new JsonObject();
        }
        String contentRaw = getElementString(root.get("content"));
        String voiceRaw = getElementString(root.get("voice"));
        boolean needSave = !configFileExists;
        return new WarningConfigRaw(contentRaw, voiceRaw, needSave, configFileExists);
    }

    public static void saveWarningConfig(JsonObject content, JsonObject voice) {
        JsonObject root = new JsonObject();
        if (content != null) {
            root.add("content", content);
        }
        if (voice != null) {
            root.add("voice", voice);
        }
        writeWarningConfig(root);
    }

    public static void saveWarningContentGroups(List<Map<String, List<String>>> contentGroups) {
        WarningGroupData data = loadWarningGroups();
        List<Map<String, List<String>>> voiceGroups = new ArrayList<>(data.voiceGroups());
        saveWarningGroups(contentGroups, voiceGroups);
    }

    public static void saveWarningGroups(List<Map<String, List<String>>> contentGroups, List<Map<String, List<String>>> voiceGroups) {
        List<Map<String, List<String>>> content = contentGroups == null ? new ArrayList<>() : new ArrayList<>(contentGroups);
        List<Map<String, List<String>>> voice = voiceGroups == null ? new ArrayList<>() : new ArrayList<>(voiceGroups);
        if (CollectionUtils.isNullOrEmpty(content)) {
            content.add(buildDefaultWarnGroup());
        }
        ensureContentDefaults(content);
        if (CollectionUtils.isNullOrEmpty(voice)) {
            voice.add(buildDefaultVoiceGroup());
        }
        saveWarningConfig(buildWarningContentJsonElement(content), buildWarningContentJsonElement(voice));
    }

    private static Path getWarningConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static String getElementString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.toString();
    }

    private static WarningContentParseResult parseWarningContent(String raw) {
        WarningContentParseResult result = new WarningContentParseResult();
        if (StringUtils.isNullOrEmpty(raw)) {
            return result;
        }
        JsonElement root;
        try {
            root = JsonUtils.GSON.fromJson(raw, JsonElement.class);
        } catch (Exception e) {
            return result;
        }
        if (root == null || root.isJsonNull()) {
            return result;
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("groups")) {
                JsonElement groupsElement = obj.get("groups");
                if (groupsElement != null && groupsElement.isJsonArray()) {
                    parseGroupsArray(groupsElement.getAsJsonArray(), result);
                } else {
                    result.markUpgrade();
                }
            } else {
                result.markUpgrade();
                Map<String, List<String>> group = parseGroupObject(obj, result);
                if (!CollectionUtils.isNullOrEmpty(group)) {
                    result.groups.add(group);
                }
            }
        } else if (root.isJsonArray()) {
            result.markUpgrade();
            parseGroupsArray(root.getAsJsonArray(), result);
        } else {
            result.markUpgrade();
        }
        return result;
    }

    private static WarningContentParseResult parseWarningVoice(String raw) {
        WarningContentParseResult result = new WarningContentParseResult();
        if (StringUtils.isNullOrEmpty(raw)) {
            return result;
        }
        JsonElement root;
        try {
            root = JsonUtils.GSON.fromJson(raw, JsonElement.class);
        } catch (Exception e) {
            return result;
        }
        if (root == null || root.isJsonNull()) {
            return result;
        }
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("groups")) {
                JsonElement groupsElement = obj.get("groups");
                if (groupsElement != null && groupsElement.isJsonArray()) {
                    parseVoiceGroupsArray(groupsElement.getAsJsonArray(), result);
                } else {
                    result.markUpgrade();
                }
            } else {
                result.markUpgrade();
                Map<String, List<String>> group = parseVoiceGroupObject(obj, result);
                if (!CollectionUtils.isNullOrEmpty(group)) {
                    result.groups.add(group);
                }
            }
        } else if (root.isJsonArray()) {
            result.markUpgrade();
            parseVoiceGroupsArray(root.getAsJsonArray(), result);
        } else {
            result.markUpgrade();
        }
        return result;
    }

    private static void parseGroupsArray(JsonArray array, WarningContentParseResult result) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                Map<String, List<String>> group = parseGroupObject(element.getAsJsonObject(), result);
                if (!CollectionUtils.isNullOrEmpty(group)) {
                    result.groups.add(group);
                }
            } else {
                result.markUpgrade();
            }
        }
    }

    private static void parseVoiceGroupsArray(JsonArray array, WarningContentParseResult result) {
        for (JsonElement element : array) {
            if (element != null && element.isJsonObject()) {
                Map<String, List<String>> group = parseVoiceGroupObject(element.getAsJsonObject(), result);
                if (!CollectionUtils.isNullOrEmpty(group)) {
                    result.groups.add(group);
                }
            } else {
                result.markUpgrade();
            }
        }
    }

    private static Map<String, List<String>> parseGroupObject(JsonObject obj, WarningContentParseResult result) {
        Map<String, List<String>> group = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            List<String> values = parseWarningValues(entry.getValue(), result);
            if (CollectionUtils.isNotNullOrEmpty(values)) {
                group.put(entry.getKey(), values);
            }
        }
        return group;
    }

    private static Map<String, List<String>> parseVoiceGroupObject(JsonObject obj, WarningContentParseResult result) {
        Map<String, List<String>> group = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            List<String> values = parseVoiceValues(entry.getValue(), result);
            if (CollectionUtils.isNotNullOrEmpty(values)) {
                group.put(entry.getKey(), values);
            }
        }
        return group;
    }

    private static List<String> parseWarningValues(JsonElement element, WarningContentParseResult result) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                addWarningValue(values, item);
            }
        } else {
            result.markUpgrade();
            addWarningValue(values, element);
        }
        return values;
    }

    private static List<String> parseVoiceValues(JsonElement element, WarningContentParseResult result) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                addVoiceValue(values, item);
            }
        } else {
            result.markUpgrade();
            addVoiceValue(values, element);
        }
        return values;
    }

    private static void addWarningValue(List<String> values, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            values.add(element.getAsJsonPrimitive().getAsString());
        } else {
            values.add(element.toString());
        }
    }

    private static void addVoiceValue(List<String> values, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            String text = element.getAsJsonPrimitive().getAsString();
            for (String item : text.split(",")) {
                String trimmed = item.trim();
                if (StringUtils.isNotNullOrEmpty(trimmed)) {
                    values.add(trimmed);
                }
            }
        } else {
            values.add(element.toString());
        }
    }

    private static List<String> defaultWarnList(String value) {
        List<String> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    private static void ensureContentDefaults(List<Map<String, List<String>>> groups) {
        for (Map<String, List<String>> group : groups) {
            group.putIfAbsent("error", defaultWarnList("§r§e香草酱坏掉了，这绝对不是香草酱的错！"));
            group.putIfAbsent("fail", defaultWarnList("§r§e香草酱什么也没吃到，失落地离开了。"));
            group.putIfAbsent("success", defaultWarnList("§r§e香草酱吃掉了[itemCount]个物品与[entityCount]个实体，并满意地离开了。"));
        }
    }

    public static Map<String, List<String>> buildDefaultWarnGroup() {
        Map<String, List<String>> group = new LinkedHashMap<>();
        group.put("error", defaultWarnList("§r§e香草酱坏掉了，这绝对不是香草酱的错！"));
        group.put("fail", defaultWarnList("§r§e香草酱什么也没吃到，失落地离开了。"));
        group.put("success", defaultWarnList("§r§e香草酱吃掉了[itemCount]个物品与[entityCount]个实体，并满意地离开了。"));
        group.put("1", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！"));
        group.put("2", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！"));
        group.put("3", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！"));
        group.put("4", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！"));
        group.put("5", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来！"));
        group.put("10", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。"));
        group.put("30", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。"));
        group.put("60", defaultWarnList("§r§e饥肠辘辘的香草酱将会在§r§e%s§r§e秒后到来。"));
        return group;
    }

    public static Map<String, List<String>> buildDefaultVoiceGroup() {
        Map<String, List<String>> group = new LinkedHashMap<>();
        group.put("error", defaultWarnList("aotake_sweep:error"));
        group.put("fail", defaultWarnList("aotake_sweep:fail"));
        group.put("success", defaultWarnList("aotake_sweep:success"));
        group.put("5", defaultWarnList("aotake_sweep:agog"));
        group.put("30", defaultWarnList("aotake_sweep:hungry"));
        return group;
    }

    private static WarningContentLoadResult loadWarningContentGroups(WarningConfigRaw raw, boolean allowLegacy) {
        String contentRaw = raw.contentRaw();
        String legacy = CommonConfig.SWEEP_WARNING_CONTENT.get();
        if (StringUtils.isNotNullOrEmpty(contentRaw)) {
            WarningContentParseResult parsed = parseWarningContent(contentRaw);
            boolean writeFile = raw.needSave() || parsed.needUpgrade() || CollectionUtils.isNullOrEmpty(parsed.groups());
            return new WarningContentLoadResult(parsed.groups(), writeFile, false);
        }
        boolean useLegacy = allowLegacy && StringUtils.isNotNullOrEmpty(legacy);
        WarningContentParseResult parsed = useLegacy ? parseWarningContent(legacy) : new WarningContentParseResult();
        boolean writeFile = raw.needSave() || CollectionUtils.isNullOrEmpty(parsed.groups());
        boolean clearLegacy = useLegacy && CollectionUtils.isNotNullOrEmpty(parsed.groups());
        return new WarningContentLoadResult(parsed.groups(), writeFile, clearLegacy);
    }

    private static WarningContentLoadResult loadWarningVoiceGroups(WarningConfigRaw raw, boolean allowLegacy) {
        String voiceRaw = raw.voiceRaw();
        String legacy = CommonConfig.SWEEP_WARNING_VOICE.get();
        if (StringUtils.isNotNullOrEmpty(voiceRaw)) {
            WarningContentParseResult parsed = parseWarningVoice(voiceRaw);
            boolean writeFile = raw.needSave() || parsed.needUpgrade() || CollectionUtils.isNullOrEmpty(parsed.groups());
            return new WarningContentLoadResult(parsed.groups(), writeFile, false);
        }
        boolean useLegacy = allowLegacy && StringUtils.isNotNullOrEmpty(legacy);
        WarningContentParseResult parsed = useLegacy ? parseWarningVoice(legacy) : new WarningContentParseResult();
        boolean writeFile = raw.needSave() || CollectionUtils.isNullOrEmpty(parsed.groups());
        boolean clearLegacy = useLegacy && CollectionUtils.isNotNullOrEmpty(parsed.groups());
        return new WarningContentLoadResult(parsed.groups(), writeFile, clearLegacy);
    }

    private static JsonObject buildWarningContentJsonElement(List<Map<String, List<String>>> groups) {
        JsonObject root = new JsonObject();
        JsonArray groupArray = new JsonArray();
        for (Map<String, List<String>> group : groups) {
            JsonObject groupObj = new JsonObject();
            for (Map.Entry<String, List<String>> entry : group.entrySet()) {
                List<String> values = entry.getValue();
                if (CollectionUtils.isNullOrEmpty(values)) {
                    continue;
                }
                JsonArray arr = new JsonArray();
                for (String value : values) {
                    arr.add(new JsonPrimitive(value));
                }
                groupObj.add(entry.getKey(), arr);
            }
            groupArray.add(groupObj);
        }
        root.add("groups", groupArray);
        return root;
    }

    private static void clearLegacyWarningContent() {
        if (StringUtils.isNotNullOrEmpty(CommonConfig.SWEEP_WARNING_CONTENT.get())) {
            CommonConfig.SWEEP_WARNING_CONTENT.set("");
            CommonConfig.save();
        }
    }

    private static void clearLegacyWarningVoice() {
        if (StringUtils.isNotNullOrEmpty(CommonConfig.SWEEP_WARNING_VOICE.get())) {
            CommonConfig.SWEEP_WARNING_VOICE.set("");
            CommonConfig.save();
        }
    }

    private static JsonObject readWarningConfigObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            return JsonUtils.GSON.fromJson(Files.readString(file.toPath()), JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to read warning config: {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static void writeWarningConfig(JsonObject root) {
        long timeout = 10;
        File file = getWarningConfigPath().toFile();
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        try (RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
             FileChannel channel = accessFile.getChannel()) {
            FileLock lock = null;
            long startTime = System.currentTimeMillis();
            while (lock == null) {
                try {
                    lock = channel.tryLock();
                } catch (Exception e) {
                    if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout)) {
                        throw new RuntimeException("Failed to acquire file lock within timeout.", e);
                    }
                    Thread.sleep(100);
                }
            }
            accessFile.setLength(0);
            accessFile.write(JsonUtils.PRETTY_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            lock.release();
        } catch (Exception e) {
            LOGGER.error("Failed to write warning config: {}", file.getAbsolutePath(), e);
        }
    }

    public record WarningConfigRaw(String contentRaw, String voiceRaw, boolean needSave, boolean configFileExists) {
    }

    public record WarningGroupData(List<Map<String, List<String>>> contentGroups,
                                   List<Map<String, List<String>>> voiceGroups) {
    }

    @Getter
    @Accessors(fluent = true)
    private static class WarningContentParseResult {
        private final List<Map<String, List<String>>> groups = new ArrayList<>();
        private boolean needUpgrade;

        public void markUpgrade() {
            this.needUpgrade = true;
        }
    }

    private record WarningContentLoadResult(List<Map<String, List<String>>> groups, boolean writeFile,
                                            boolean clearLegacy) {
    }
}
