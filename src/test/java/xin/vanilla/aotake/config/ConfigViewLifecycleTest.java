package xin.vanilla.aotake.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.ConfigCategoryTitleSpec;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.common.config.annotation.Config;
import xin.vanilla.banira.internal.forge.config.ForgeConfigAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

public class ConfigViewLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void bootstrap() throws Exception { xin.vanilla.aotake.config.ConfigViewBaselineTest.bootstrapRegistryDefaults(); }

    @Test public void retainedRuleListFollowsFileReloadAndRebinding() throws Exception { verify(false); }
    @Test public void retainedClientCategoryFollowsFileReloadAndRebinding() throws Exception { verify(true); }

    private void verify(boolean client) throws Exception {
        Class<?> type = client ? ClientConfig.class : CommonConfig.class;
        String key = client ? "progressBar.text.progressBarTextPosition" : "base.sweep.entityList";
        Object externalValue = client ? "40%,8" : Arrays.asList("tick, clazz, itemClazz -> tick >= 5", "minecraft:arrow");
        Object writtenValue = client ? "60%,9" : Collections.singletonList("resource -> resource != 'minecraft:air'");
        try (FileFixture first = new FileFixture(type, temporary.newFile("first.toml").toPath());
             FileFixture second = new FileFixture(type, temporary.newFile("second.toml").toPath())) {
            ConfigBaselineFixture.bind(type, first.holder);
            Supplier<Object> read;
            Consumer<Object> write;
            if (client) {
                ClientConfigView.ProgressBarView.TextView retained = ClientConfigView.get().progressBar().text();
                read = retained::progressBarTextPosition;
                write = value -> retained.progressBarTextPosition((String) value);
            } else {
                CommonConfigView.BaseView.SweepView retained = CommonConfigView.get().base().sweep();
                read = retained::entityList;
                write = value -> retained.entityList((List<String>) value);
            }
            Object original = read.get();
            List<Object> reloads = new ArrayList<>();
            first.holder.onReloaded(paths -> reloads.add(read.get()));
            CommentedConfig disk = parse(first.path);
            disk.set(key, externalValue);
            Files.write(first.path, TomlFormat.instance().createWriter().writeToString(disk).getBytes(StandardCharsets.UTF_8));
            first.file.load();
            first.invoke("acceptReload", new Class<?>[0]);
            assertEquals(externalValue, read.get());
            assertEquals(Collections.singletonList(externalValue), reloads);

            ConfigBaselineFixture.bind(type, second.holder);
            assertEquals(original, read.get());
            write.accept(writtenValue);
            assertEquals(writtenValue, second.holder.get(key));
            assertEquals(externalValue, first.holder.get(key));
            second.holder.save();
            assertEquals(writtenValue, parse(second.path).get(key));
            ConfigBaselineFixture.bind(type, null);
            assertEquals(original, read.get());
            write.accept(externalValue);
            assertEquals(writtenValue, second.holder.get(key));
            ConfigBaselineFixture.bind(type, first.holder);
            assertEquals(externalValue, read.get());
        } finally { ConfigBaselineFixture.bind(type, null); }
    }

    private static CommentedConfig parse(Path path) throws Exception {
        return new TomlParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
    }

    // Reflection stays in the test: exercise the real backend without publishing its internals.
    private static final class FileFixture implements AutoCloseable {
        final Path path;
        final Object backend;
        final ConfigHolder holder;
        final CommentedFileConfig file;

        FileFixture(Class<?> type, Path path) throws Exception {
            this.path = path;
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
            List<ConfigEntryDescriptor> descriptors = new ArrayList<>();
            Map<String, ForgeConfigSpec.ConfigValue<?>> values = new LinkedHashMap<>();
            Map<String, String> tooltips = new LinkedHashMap<>();
            Map<String, ConfigCategoryTitleSpec> titles = new LinkedHashMap<>();
            Method scan = ForgeConfigAdapter.class.getDeclaredMethod("buildFromClass", ForgeConfigSpec.Builder.class,
                    Class.class, String.class, List.class, Map.class, Map.class, Map.class);
            scan.setAccessible(true);
            scan.invoke(null, builder, type, "", descriptors, values, tooltips, titles);
            ForgeConfigSpec spec = builder.build();
            Class<?> backendType = Class.forName("xin.vanilla.banira.internal.forge.config.ForgeConfigValueStore");
            Constructor<?> constructor = backendType.getDeclaredConstructor(ForgeConfigSpec.class, Map.class);
            constructor.setAccessible(true);
            backend = constructor.newInstance(spec, values);
            Config config = type.getAnnotation(Config.class);
            holder = ConfigHolder.create("fixture", config.name(), config.type(), (ConfigValueStore) backend,
                    descriptors, tooltips, titles);
            invoke("setHolder", new Class<?>[]{ConfigHolder.class}, holder);
            file = (CommentedFileConfig) invoke("wrap", new Class<?>[]{CommentedFileConfig.class},
                    CommentedFileConfig.builder(path).sync().build());
            file.load();
            spec.setConfig(file);
            holder.acceptInitialExternalLoad();
        }

        Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
            Method method = backend.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(backend, args);
        }

        public void close() { file.close(); }
    }
}
