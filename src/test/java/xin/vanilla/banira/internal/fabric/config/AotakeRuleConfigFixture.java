package xin.vanilla.banira.internal.fabric.config;

import xin.vanilla.aotake.rule.RuleMigrationTransaction;
import xin.vanilla.banira.common.config.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

/** Test-only access to Fabric's real TOML backend, including bypassed in-memory edits. */
public final class AotakeRuleConfigFixture implements AutoCloseable {
    public final ConfigHolder holder;
    private final FabricConfigValueStore store;

    public AotakeRuleConfigFixture(Path config) {
        List<ConfigEntryDescriptor> descriptors = new ArrayList<>();
        for (String path : RuleMigrationTransaction.LISTS) descriptors.add(ConfigEntryDescriptor.builder()
                .path(path).valueType(ConfigEntryDescriptor.ConfigValueType.STRING_LIST)
                .defaultValue(Arrays.asList("minecraft:arrow", "tick -> tick >= 5", "minecraft:*")).build());
        for (String path : RuleMigrationTransaction.MODES) descriptors.add(ConfigEntryDescriptor.builder()
                .path(path).valueType(ConfigEntryDescriptor.ConfigValueType.STRING).defaultValue("WHITE").build());
        descriptors.add(ConfigEntryDescriptor.builder().path("base.sweep.sweepPolicyId")
                .valueType(ConfigEntryDescriptor.ConfigValueType.STRING).defaultValue("builtin:default").build());
        descriptors.add(ConfigEntryDescriptor.builder().path("unrelated")
                .valueType(ConfigEntryDescriptor.ConfigValueType.INTEGER).defaultValue(1).minValue(0).maxValue(20).build());
        store = new FabricConfigValueStore(config, descriptors);
        holder = ConfigHolder.create("test", "test-common", ConfigScope.COMMON, store,
                descriptors, Collections.emptyMap(), Collections.emptyMap());
        holder.acceptInitialExternalLoad();
    }

    @SuppressWarnings("unchecked")
    public void editNativeRule(String path, int index, String rule) {
        try {
            Field field = FabricConfigValueStore.class.getDeclaredField("values");
            field.setAccessible(true);
            Map<String, Object> values = (Map<String, Object>) field.get(store);
            ((List<String>) values.get(path)).set(index, rule);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot simulate bypassed native config edit", error);
        }
    }

    @Override public void close() { }
}
