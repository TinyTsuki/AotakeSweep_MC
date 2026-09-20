package xin.vanilla.aotake.config;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.internal.fabric.config.FabricBaniraConfigService;
import xin.vanilla.banira.platform.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static org.junit.Assert.*;

public class ConfigViewLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private BaniraPlatform previous;
    @BeforeClass public static void bootstrap() { net.minecraft.server.Bootstrap.bootStrap(); }
    @Before public void rememberPlatform() { previous = BaniraPlatforms.get(); }
    @After public void restorePlatform() { BaniraPlatforms.install(previous); }
    @Test public void retainedRulesFollowRealFileReloadAndRebinding() throws Exception { verify(false); }
    @Test public void retainedTextPositionFollowsRealFileReloadAndRebinding() throws Exception { verify(true); }

    private void verify(boolean client) throws Exception {
        Path directory = temporary.newFolder().toPath();
        install(directory, FabricBaniraConfigService.INSTANCE);
        Class<?> type = client ? ClientConfig.class : CommonConfig.class;
        String key = client ? "progressBar.text.progressBarTextPosition" : "base.sweep.entityList";
        FabricBaniraConfigService.INSTANCE.register(type, "aotake_sweep");
        ConfigHolder original = holder(type);
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
        Object initial = read.get();
        Path file = directory.resolve(original.getConfigName() + ".toml");
        for (int i = 0; i < 20; i++) {
            Object changed = client ? (40 + i) + "%,8"
                    : Arrays.asList("tick, clazz -> tick >= " + (i + 5), "minecraft:arrow");
            ConfigValueStore external = disk(file, original);
            external.set(key, changed);
            external.save();
            FabricBaniraConfigService.INSTANCE.register(type, "aotake_sweep");
            assertNotSame(original, holder(type));
            assertEquals(changed, read.get());
            assertEquals(initial, original.get(key));
        }
        Object saved = client ? "60%,9" : Collections.singletonList("resource -> resource != 'minecraft:air'");
        write.accept(saved);
        ConfigHolder latest = holder(type);
        latest.save();
        assertEquals(saved, disk(file, latest).get(key));
        ConfigBaselineFixture.bind(type, null);
        assertEquals(initial, read.get());
        write.accept(initial);
        assertEquals(saved, latest.get(key));
        install(directory, FabricBaniraConfigService.INSTANCE);
        assertEquals(saved, read.get());
    }

    @Test public void declarationBeansRetainFluentAccessorsWithoutExposingRootFields() throws Exception {
        CommonConfig.SweepSection sweep = new CommonConfig.SweepSection();
        assertSame(sweep, sweep.sweepInterval(12345L));
        assertEquals(12345L, sweep.sweepInterval());
        ClientConfig.ProgressBarTextCategory text = new ClientConfig.ProgressBarTextCategory();
        assertSame(text, text.progressBarTextPosition("50%,12"));
        assertEquals("50%,12", text.progressBarTextPosition());
        for (Class<?> root : Arrays.asList(CommonConfig.class, ClientConfig.class)) {
            for (Field field : root.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                for (Method method : root.getDeclaredMethods()) {
                    assertFalse("Declaration field exposed: " + field.getName(),
                            method.getName().equals(field.getName()) && !Modifier.isStatic(method.getModifiers()));
                }
            }
        }
    }

    private static ConfigHolder holder(Class<?> type) {
        return (ConfigHolder) FabricBaniraConfigService.INSTANCE.handle(type);
    }
    private static ConfigValueStore disk(Path path, ConfigHolder holder) throws Exception {
        Class<?> type = Class.forName("xin.vanilla.banira.internal.fabric.config.FabricConfigValueStore");
        Constructor<?> constructor = type.getDeclaredConstructor(Path.class, List.class);
        constructor.setAccessible(true);
        return (ConfigValueStore) constructor.newInstance(path, holder.getDescriptors());
    }
    private static void install(Path directory, BaniraConfigService service) {
        BaniraPlatforms.install((BaniraPlatform) Proxy.newProxyInstance(
                ConfigViewLifecycleTest.class.getClassLoader(), new Class<?>[]{BaniraPlatform.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("configService")) return service;
                    if (method.getName().equals("configDir")) return directory;
                    throw new UnsupportedOperationException(method.toString());
                }));
    }
}
