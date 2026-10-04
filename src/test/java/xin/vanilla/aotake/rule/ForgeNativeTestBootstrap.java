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
    public static synchronized void bootstrapGame() {
        try {
            Field state = net.minecraftforge.fml.loading.FMLLoader.class.getDeclaredField("loadingModList");
            state.setAccessible(true);
            if (state.get(null) == null) {
                net.minecraftforge.fml.loading.LoadingModList loading = net.minecraftforge.fml.loading.LoadingModList.of(
                        java.util.List.of(), java.util.List.of(), null);
                loading.setBrokenFiles(java.util.List.of());
                state.set(null, loading);
                cpw.mods.modlauncher.api.IModuleLayerManager layers = layer -> java.util.Optional.of(ModuleLayer.boot());
                setField(net.minecraftforge.fml.loading.FMLLoader.class, "moduleLayerManager", layers);
                net.minecraftforge.fml.ModList mods = net.minecraftforge.fml.ModList.of(java.util.List.of(), java.util.List.of());
                java.lang.reflect.Method loaded = net.minecraftforge.fml.ModList.class.getDeclaredMethod("setLoadedMods", java.util.List.class);
                loaded.setAccessible(true);
                loaded.invoke(mods, java.util.List.of());
                net.minecraftforge.fml.ModLoader.get();
                setField(net.minecraftforge.fml.ModLoader.class, "loadingStateValid", true);
            }
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
            initialize();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void setField(Class<?> type, String name, Object value) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
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
