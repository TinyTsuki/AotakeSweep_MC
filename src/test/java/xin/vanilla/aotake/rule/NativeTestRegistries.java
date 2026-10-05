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
    public static void bootstrap() throws ReflectiveOperationException {
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Standalone JUnit has no NeoForge registration bus; bind only the native empty fluid holder.
        net.minecraft.core.Holder<net.neoforged.neoforge.fluids.FluidType> empty =
                net.neoforged.neoforge.common.NeoForgeMod.EMPTY_TYPE;
        if (!empty.isBound()) {
            Field holder = net.neoforged.neoforge.registries.DeferredHolder.class.getDeclaredField("holder");
            holder.setAccessible(true);
            holder.set(empty, net.minecraft.core.Holder.direct(new net.neoforged.neoforge.fluids.FluidType(
                    net.neoforged.neoforge.fluids.FluidType.Properties.create())));
        }
    }
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
