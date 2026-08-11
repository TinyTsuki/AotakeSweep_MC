package xin.vanilla.aotake.command;

import org.junit.Test;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.banira.common.config.ConfigCategoryTitleSpec;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigScope;
import xin.vanilla.banira.common.config.ConfigValueStore;
import xin.vanilla.banira.platform.BaniraConfigHandle;
import xin.vanilla.banira.platform.BaniraConfigService;
import xin.vanilla.banira.platform.BaniraInputService;
import xin.vanilla.banira.platform.BaniraLogoService;
import xin.vanilla.banira.platform.BaniraNetworkService;
import xin.vanilla.banira.platform.BaniraNotificationService;
import xin.vanilla.banira.platform.BaniraPathService;
import xin.vanilla.banira.platform.BaniraPlayerDataService;
import xin.vanilla.banira.platform.BaniraPlatform;
import xin.vanilla.banira.platform.BaniraPlatforms;
import xin.vanilla.banira.platform.BaniraRegistryService;
import xin.vanilla.banira.platform.BaniraServerService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 命令名由 Banira 配置视图驱动，迁移版本时必须保持同一套语义。
 */
public class CommandConfigContractTest {

    @Test
    public void defaultCommandConfigProducesStableCommands() {
        installPlatform(new MapStore());

        assertEquals("aotake help", AotakeUtils.getCommand(EnumCommandType.HELP));
        assertEquals("aotake language", AotakeUtils.getCommand(EnumCommandType.LANGUAGE));
        assertEquals("aotake opv", AotakeUtils.getCommand(EnumCommandType.VIRTUAL_OP));
        assertEquals("aotake dustbin", AotakeUtils.getCommand(EnumCommandType.DUSTBIN_OPEN));
        assertEquals("aotake killitem", AotakeUtils.getCommand(EnumCommandType.CLEAR_DROP));
        assertEquals("killitem", AotakeUtils.getCommand(EnumCommandType.CLEAR_DROP_CONCISE));
        assertFalse(AotakeUtils.isConciseEnabled(EnumCommandType.LANGUAGE));
        assertTrue(AotakeUtils.isConciseEnabled(EnumCommandType.CLEAR_DROP));
        assertEquals(4, AotakeUtils.getCommandPermissionLevel(EnumCommandType.VIRTUAL_OP));
        assertEquals(0, AotakeUtils.getCommandPermissionLevel(EnumCommandType.DUSTBIN_OPEN));
    }

    @Test
    public void commandConfigOverridesAreVisibleThroughPublicHelpers() {
        MapStore store = new MapStore();
        store.values.put("command.commandPrefix", "sweep");
        store.values.put("command.commandLanguage", "lang");
        store.values.put("concise.conciseLanguage", true);
        store.values.put("permission.permissionVirtualOp", 2);
        installPlatform(store);

        assertEquals("sweep lang", AotakeUtils.getCommand(EnumCommandType.LANGUAGE));
        assertEquals("lang", AotakeUtils.getCommand(EnumCommandType.LANGUAGE_CONCISE));
        assertTrue(AotakeUtils.isConciseEnabled(EnumCommandType.LANGUAGE));
        assertEquals(2, AotakeUtils.getCommandPermissionLevel(EnumCommandType.VIRTUAL_OP));
    }

    private static void installPlatform(MapStore store) {
        ConfigHolder holder = ConfigHolder.create("aotake_sweep", "aotake-common", ConfigScope.COMMON, store,
                Collections.<ConfigEntryDescriptor>emptyList(),
                Collections.<String, String>emptyMap(),
                Collections.<String, ConfigCategoryTitleSpec>emptyMap());
        BaniraPlatforms.install(new TestPlatform(new TestConfigService().holder(CommonConfig.class, holder)));
    }

    private static final class TestConfigService implements BaniraConfigService {
        private final Map<Class<?>, BaniraConfigHandle> holders = new LinkedHashMap<>();

        private TestConfigService holder(Class<?> configClass, ConfigHolder holder) {
            holders.put(configClass, holder);
            return this;
        }

        @Override
        public <T> void register(@Nonnull Class<T> configClass, @Nonnull String modId) {
        }

        @Nonnull
        @Override
        public <T> T view(@Nonnull Class<?> configClass, @Nonnull Class<T> viewClass) {
            throw new UnsupportedOperationException("view");
        }

        @Nullable
        @Override
        public BaniraConfigHandle handle(@Nonnull Class<?> configClass) {
            return holders.get(configClass);
        }
    }

    private static final class MapStore implements ConfigValueStore {
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public Set<String> paths() {
            return values.keySet();
        }

        @Nullable
        @Override
        public Object get(String path) {
            return values.get(path);
        }

        @Override
        public void set(String path, Object value) {
            values.put(path, value);
        }

        @Override
        public Class<?> valueClass(String path) {
            Object value = values.get(path);
            return value == null ? Object.class : value.getClass();
        }

        @Nullable
        @Override
        public Object defaultValue(String path) {
            return null;
        }

        @Override
        public boolean validate(String path, Object value) {
            return true;
        }

        @Override
        public void save() {
        }
    }

    private static final class TestPlatform implements BaniraPlatform {
        private final BaniraConfigService configService;
        private final BaniraPathService pathService = new TestPathService();

        private TestPlatform(BaniraConfigService configService) {
            this.configService = configService;
        }

        @Nonnull
        @Override
        public String loaderType() {
            return "test";
        }

        @Nonnull
        @Override
        public String minecraftVersion() {
            return "1.16.5";
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isDedicatedServer() {
            return true;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public boolean isModLoaded(@Nonnull String modId) {
            return "aotake_sweep".equals(modId) || "banira_codex".equals(modId);
        }

        @Nonnull
        @Override
        public String modDisplayName(@Nonnull String modId) {
            return modId;
        }

        @Nullable
        @Override
        public String lastKnownUsername(@Nonnull UUID uuid) {
            return null;
        }

        @Nonnull
        @Override
        public String modIdFromMainClass(@Nonnull Class<?> modMainClass) {
            return "test";
        }

        @Nonnull
        @Override
        public Class<?> modMainClass(@Nonnull String modId) {
            return TestPlatform.class;
        }

        @Nonnull
        @Override
        public Path configDir() {
            return Paths.get("config");
        }

        @Nonnull
        @Override
        public BaniraPathService pathService() {
            return pathService;
        }

        @Nonnull
        @Override
        public BaniraConfigService configService() {
            return configService;
        }

        @Nonnull
        @Override
        public BaniraServerService serverService() {
            return noop(BaniraServerService.class);
        }

        @Nonnull
        @Override
        public BaniraPlayerDataService playerDataService() {
            return noop(BaniraPlayerDataService.class);
        }

        @Nonnull
        @Override
        public BaniraNetworkService networkService() {
            return noop(BaniraNetworkService.class);
        }

        @Nonnull
        @Override
        public BaniraRegistryService registryService() {
            return noop(BaniraRegistryService.class);
        }

        @Nonnull
        @Override
        public BaniraInputService inputService() {
            return noop(BaniraInputService.class);
        }

        @Nonnull
        @Override
        public BaniraNotificationService notificationService() {
            return noop(BaniraNotificationService.class);
        }

        @Nonnull
        @Override
        public BaniraLogoService logoService() {
            return noop(BaniraLogoService.class);
        }
    }

    private static final class TestPathService implements BaniraPathService {
        @Nonnull
        @Override
        public String rootDirectoryName() {
            return "vanilla.xin";
        }

        @Nonnull
        @Override
        public Path configPath() {
            return Paths.get("config", rootDirectoryName());
        }

        @Nonnull
        @Override
        public Path gameConfigPath() {
            return Paths.get("config");
        }

        @Nonnull
        @Override
        public Path worldDataPath() {
            return Paths.get("build", "test-world", rootDirectoryName());
        }

        @Nonnull
        @Override
        public Path playerDataPath() {
            return worldDataPath().resolve("playerdata");
        }

        @Nonnull
        @Override
        public Path vanillaPlayerDataPath() {
            return Paths.get("build", "test-world", "playerdata");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T noop(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) {
                return null;
            }
            if (returnType == Boolean.TYPE) {
                return false;
            }
            if (returnType.isPrimitive()) {
                return 0;
            }
            if (returnType == Set.class || returnType == Collection.class) {
                return Collections.emptySet();
            }
            return null;
        });
    }
}
