package xin.vanilla.aotake.rule;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import com.mojang.serialization.Lifecycle;
import java.lang.reflect.Field;
import java.util.Collections;

public final class NativeTestRegistries {
    private NativeTestRegistries() { }
    @SuppressWarnings("unchecked")
    public static RegistryAccess damageTypes() throws IllegalAccessException {
        MappedRegistry<DamageType> registry = new MappedRegistry<>(Registries.DAMAGE_TYPE, Lifecycle.stable());
        for (Field field : DamageTypes.class.getFields()) {
            if (field.getType() != ResourceKey.class) continue;
            ResourceKey<DamageType> key = (ResourceKey<DamageType>) field.get(null);
            registry.register(key, new DamageType(key.location().getPath(), 0F),
                    net.minecraft.core.RegistrationInfo.BUILT_IN);
        }
        return new RegistryAccess.ImmutableRegistryAccess(Collections.singletonList(registry)).freeze();
    }
}
