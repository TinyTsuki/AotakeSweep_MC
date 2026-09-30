package xin.vanilla.aotake.sweep;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.banira.common.config.ConfigEditSnapshot;
import xin.vanilla.banira.common.util.JsonUtils;

/** Task-local data, decoded once from the lease; never rebinds the live generated view. */
public final class SweepConfiguration {
    private static final Gson JSON = new Gson();
    private SweepConfiguration() { }
    public static CommonConfig.BaseCategory base(ConfigEditSnapshot snapshot) {
        JsonObject root = new JsonObject();
        snapshot.getValues().forEach((path, value) -> {
            if (path.startsWith("base.")) JsonUtils.setJsonElement(root, path.substring(5), JSON.toJsonTree(value));
        });
        return JSON.fromJson(root, CommonConfig.BaseCategory.class);
    }
}
