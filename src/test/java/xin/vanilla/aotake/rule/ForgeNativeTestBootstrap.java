package xin.vanilla.aotake.rule;

import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;
import java.lang.reflect.Field;

/** Supplies loader-owned fluid registrations for isolated JUnit entity construction. */
public final class ForgeNativeTestBootstrap {
    private ForgeNativeTestBootstrap() { }
    public static synchronized void initialize() {
        if (ForgeMod.EMPTY_TYPE.isPresent()) return;
        try {
            IForgeRegistry<FluidType> registry = ForgeRegistries.FLUID_TYPES.get();
            if (registry == null) {
                RegistryBuilder<FluidType> builder = new RegistryBuilder<FluidType>()
                        .setName(ForgeRegistries.Keys.FLUID_TYPES.location());
                java.lang.reflect.Method create = RegistryBuilder.class.getDeclaredMethod("create");
                create.setAccessible(true);
                registry = (IForgeRegistry<FluidType>) create.invoke(builder);
            }
            Field value = RegistryObject.class.getDeclaredField("value");
            value.setAccessible(true);
            for (RegistryObject<FluidType> reference : java.util.Arrays.asList(ForgeMod.EMPTY_TYPE, ForgeMod.WATER_TYPE, ForgeMod.LAVA_TYPE)) {
                FluidType type = new FluidType(FluidType.Properties.create());
                registry.register(reference.getId(), type);
                value.set(reference, type);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
