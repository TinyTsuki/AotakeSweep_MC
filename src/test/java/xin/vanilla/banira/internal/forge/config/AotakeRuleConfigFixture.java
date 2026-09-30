package xin.vanilla.banira.internal.forge.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import xin.vanilla.aotake.rule.RuleMigrationTransaction;
import xin.vanilla.banira.common.config.*;
import java.nio.file.Path;
import java.util.*;

/** Test-only access to the real backend without widening Banira's production API. */
public final class AotakeRuleConfigFixture implements AutoCloseable {
    public final ConfigHolder holder;
    private final CommentedFileConfig file;
    public AotakeRuleConfigFixture(Path config) {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        Map<String, ForgeConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
        for (String path : RuleMigrationTransaction.LISTS) values.put(path, builder.defineList(path,
                Arrays.asList("minecraft:arrow", "tick -> tick >= 5", "minecraft:*"), value -> value instanceof String));
        for (String path : RuleMigrationTransaction.MODES) values.put(path, builder.define(path, "WHITE"));
        values.put("unrelated", builder.defineInRange("unrelated", 1, 0, 20));
        ForgeConfigSpec spec = builder.build(); ForgeConfigBackend backend = new ForgeConfigBackend(spec, values);
        holder = ConfigHolder.create("test", "test-common", ConfigScope.COMMON, backend, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
        backend.setHolder(holder); file = backend.wrap(CommentedFileConfig.of(config)); file.load(); spec.setConfig(file); holder.acceptInitialExternalLoad(); file.save();
    }
    @Override public void close() { file.close(); }
}
