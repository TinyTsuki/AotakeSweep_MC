package xin.vanilla.banira.internal.neoforge.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import xin.vanilla.aotake.rule.RuleMigrationTransaction;
import xin.vanilla.banira.common.config.*;
import java.nio.file.Path;
import java.util.*;

/** Test-only access to the real backend without widening Banira's production API. */
public final class AotakeRuleConfigFixture implements AutoCloseable {
    public final ConfigHolder holder;
    private final Map<String, ModConfigSpec.ConfigValue<?>> values;
    private final CommentedFileConfig file;
    public AotakeRuleConfigFixture(Path config) throws java.io.IOException {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        values = new LinkedHashMap<>();
        for (String path : RuleMigrationTransaction.LISTS) values.put(path, builder.defineList(path,
                Arrays.asList("minecraft:arrow", "tick -> tick >= 5", "minecraft:*"), value -> value instanceof String));
        for (String path : RuleMigrationTransaction.MODES) values.put(path, builder.define(path, "WHITE"));
        values.put("base.sweep.sweepPolicyId", builder.define("base.sweep.sweepPolicyId", "builtin:default"));
        values.put("unrelated", builder.defineInRange("unrelated", 1, 0, 20));
        ModConfigSpec spec = builder.build(); NeoForgeConfigValueStore backend = new NeoForgeConfigValueStore(spec, values);
        holder = ConfigHolder.create("test", "test-common", ConfigScope.COMMON, backend, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
        backend.setHolder(holder);
        // NightConfig 3.8's default empty-file action retains its bootstrap writer on Windows.
        if (!java.nio.file.Files.exists(config)) java.nio.file.Files.createFile(config);
        CommentedFileConfig nativeFile = CommentedFileConfig.builder(config).sync().build();
        nativeFile.load();
        spec.correct(nativeFile);
        nativeFile.save();
        file = backend.wrap(nativeFile);
        file.load(); bindLoaded(spec); holder.acceptInitialExternalLoad(); file.save();
    }
    private void bindLoaded(ModConfigSpec spec) {
        try {
            java.lang.reflect.Constructor<?> constructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
                    .getDeclaredConstructor(com.electronwill.nightconfig.core.CommentedConfig.class,
                            Path.class, net.neoforged.fml.config.ModConfig.class);
            constructor.setAccessible(true);
            java.lang.reflect.Field field = ModConfigSpec.class.getDeclaredField("loadedConfig");
            field.setAccessible(true);
            field.set(spec, constructor.newInstance(file, null, null));
            spec.afterReload();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    @SuppressWarnings("unchecked")
    public void editNativeRule(String path, int index, String rule) {
        ((List<String>) values.get(path).get()).set(index, rule);
    }
    @Override public void close() { file.close(); }
}
