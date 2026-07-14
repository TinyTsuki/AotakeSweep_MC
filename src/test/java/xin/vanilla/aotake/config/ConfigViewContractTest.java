package xin.vanilla.aotake.config;

import org.junit.Test;
import xin.vanilla.aotake.config.access.ClientConfigAccess;
import xin.vanilla.aotake.config.access.CommonConfigAccess;
import xin.vanilla.aotake.enums.EnumDustbinClientUiStyle;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.banira.common.config.ConfigCategoryTitleSpec;
import xin.vanilla.banira.common.config.ConfigEntryDescriptor;
import xin.vanilla.banira.common.config.ConfigHolder;
import xin.vanilla.banira.common.config.ConfigScope;
import xin.vanilla.banira.common.config.ConfigValueStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * 锁定 Aotake 通过 Banira 配置视图暴露给业务层的基础契约。
 */
public class ConfigViewContractTest {

    private static final String MOD_ID = "aotake_sweep";
    private static final String DEFAULT_COMMAND_PREFIX = "aotake";

    @Test
    public void commonConfigViewFallsBackToDefaultsWithoutHolder() {
        CommonConfig.RootView root = CommonConfigAccess.root(null);

        assertNull(root.holder());
        assertEquals(DEFAULT_COMMAND_PREFIX, root.command().commandPrefix());
        assertEquals("language", root.command().commandLanguage());
        assertEquals("opv", root.command().commandVirtualOp());
        assertEquals("dustbin", root.command().commandDustbinOpen());
        assertEquals("chunkvault", root.command().commandChunkVault());
        assertFalse(root.concise().conciseLanguage());
        assertEquals(4, root.permission().permissionVirtualOp());
        assertEquals(0, root.permission().permissionDustbinOpen());
        assertEquals(2, root.permission().permissionDustbinOpenOther());
    }

    @Test
    public void clientConfigViewFallsBackToDefaultsWithoutHolder() {
        ClientConfig.RootView root = ClientConfigAccess.root(null);

        assertNull(root.holder());
        assertEquals(Arrays.asList(EnumProgressBarType.LEAF), root.progressBar().progressBarDisplayNormal());
        assertEquals(Arrays.asList(EnumProgressBarType.LEAF, EnumProgressBarType.POLE, EnumProgressBarType.TEXT),
                root.progressBar().progressBarDisplayHold());
        assertFalse(root.progressBar().progressBarKeyApplyMode());
        assertEquals("50%,29", root.progressBar().pole().progressBarPolePosition());
        assertEquals("50%,8", root.progressBar().text().progressBarTextPosition());
        assertSame(EnumDustbinClientUiStyle.TEXTURED, root.dustbin().dustbinUiStyle());
    }

    @Test
    public void configViewsWriteToStableBaniraPaths() {
        MapStore store = new MapStore(
                "command.commandPrefix",
                "permission.permissionVirtualOp",
                "progressBar.progressBarKeyApplyMode",
                "dustbin.dustbinUiStyle");
        ConfigHolder commonHolder = holder("aotake-common", ConfigScope.COMMON, store);
        ConfigHolder clientHolder = holder("aotake-client", ConfigScope.CLIENT, store);

        CommonConfigAccess.root(commonHolder).command().commandPrefix("sweep");
        CommonConfigAccess.root(commonHolder).permission().permissionVirtualOp(3);
        ClientConfigAccess.root(clientHolder).progressBar().progressBarKeyApplyMode(true);
        ClientConfigAccess.root(clientHolder).dustbin().dustbinUiStyle(EnumDustbinClientUiStyle.BANIRA_THEME);

        assertEquals("sweep", store.values.get("command.commandPrefix"));
        assertEquals(3, store.values.get("permission.permissionVirtualOp"));
        assertEquals(true, store.values.get("progressBar.progressBarKeyApplyMode"));
        assertSame(EnumDustbinClientUiStyle.BANIRA_THEME, store.values.get("dustbin.dustbinUiStyle"));
    }

    private static ConfigHolder holder(String name, ConfigScope scope, ConfigValueStore store) {
        return ConfigHolder.create(MOD_ID, name, scope, store,
                Collections.<ConfigEntryDescriptor>emptyList(),
                Collections.<String, String>emptyMap(),
                Collections.<String, ConfigCategoryTitleSpec>emptyMap());
    }

    private static final class MapStore implements ConfigValueStore {
        private final Map<String, Object> values = new LinkedHashMap<>();

        private MapStore(String... paths) {
            for (String path : paths) {
                values.put(path, null);
            }
        }

        @Override
        public Set<String> paths() {
            return values.keySet();
        }

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
            return value != null ? value.getClass() : Object.class;
        }

        @Override
        public Object defaultValue(String path) {
            return null;
        }

        @Override
        public boolean validate(String path, Object value) {
            return values.containsKey(path);
        }

        @Override
        public void save() {
        }
    }
}
