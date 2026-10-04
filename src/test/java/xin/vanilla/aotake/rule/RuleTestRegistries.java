package xin.vanilla.aotake.rule;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import java.util.ArrayList;
import java.util.List;

public final class RuleTestRegistries {
    private RuleTestRegistries() { }
    public static RegistryAccess access() { return Lazy.ACCESS; }
    private static final class Lazy {
        static final RegistryAccess ACCESS = create();
        private static RegistryAccess create() {
            HolderLookup.Provider lookup = new RegistrySetBuilder().add(Registries.DAMAGE_TYPE, DamageTypes::bootstrap).build(RegistryAccess.EMPTY);
            MappedRegistry<DamageType> damage = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
            lookup.lookupOrThrow(Registries.DAMAGE_TYPE).listElements().forEach(holder ->
                    damage.register(holder.unwrapKey().get(), holder.value(), RegistrationInfo.BUILT_IN));
            damage.freeze();
            List<Registry<?>> registries = new ArrayList<>();
            for (Registry<?> registry : BuiltInRegistries.REGISTRY) registries.add(registry);
            registries.add(damage);
            return new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        }
    }
}
