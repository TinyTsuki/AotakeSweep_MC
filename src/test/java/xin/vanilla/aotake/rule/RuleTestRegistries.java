package xin.vanilla.aotake.rule;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;

public final class RuleTestRegistries {
    private RuleTestRegistries() { }
    public static RegistryAccess access() { return Lazy.ACCESS; }
    private static final class Lazy {
        static final RegistryAccess ACCESS = create();
        private static RegistryAccess create() {
            HolderLookup.Provider lookup = VanillaRegistries.createLookup();
            List<Registry<?>> registries = new ArrayList<>();
            for (Registry<?> registry : BuiltInRegistries.REGISTRY) registries.add(registry);
            registries.add(registry(lookup, Registries.DAMAGE_TYPE));
            registries.add(registry(lookup, Registries.BIOME));
            registries.add(registry(lookup, Registries.DIMENSION_TYPE));
            return new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        }

        private static <T> Registry<T> registry(HolderLookup.Provider lookup, ResourceKey<Registry<T>> key) {
            MappedRegistry<T> registry = new MappedRegistry<>(key, Lifecycle.stable());
            lookup.lookupOrThrow(key).listElements().forEach(holder ->
                    registry.register(holder.key(), holder.value(), RegistrationInfo.BUILT_IN));
            return registry.freeze();
        }
    }
}
