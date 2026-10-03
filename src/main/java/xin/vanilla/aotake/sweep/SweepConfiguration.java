package xin.vanilla.aotake.sweep;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.common.config.ConfigReadSnapshot;

/** Task-local data, decoded once from the lease; never rebinds the live generated view. */
public final class SweepConfiguration {
    private static final Gson JSON = new Gson();
    private SweepConfiguration() { }
    public static CommonConfig.BaseCategory base(ConfigReadSnapshot snapshot) {
        JsonObject root = new JsonObject();
        snapshot.getValues().forEach((path, value) -> {
            if (!path.startsWith("base.")) return;
            JsonObject target = root;
            int start = 5, end;
            // Schema paths contain Java property names, without array indices or escaped separators.
            while ((end = path.indexOf('.', start)) >= 0) {
                String key = path.substring(start, end);
                JsonObject child = target.getAsJsonObject(key);
                if (child == null) { child = new JsonObject(); target.add(key, child); }
                target = child;
                start = end + 1;
            }
            target.add(path.substring(start), JSON.toJsonTree(value));
        });
        return JSON.fromJson(root, CommonConfig.BaseCategory.class);
    }
}
