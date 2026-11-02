package xin.vanilla.aotake.client.ui;

import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ChestContainer;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class DustbinUiSessionTest {
    @BeforeClass
    public static void bootstrap() {
        installPlatform();
        net.minecraft.util.registry.Bootstrap.bootStrap();
    }

    private static void installPlatform() {
        xin.vanilla.banira.platform.BaniraConfigService config = new xin.vanilla.banira.platform.BaniraConfigService() {
            public <T> void register(Class<T> type, String modId) {
            }

            public <T> T view(Class<?> type, Class<T> view) {
                throw new IllegalStateException("No configuration registered in UI fixture");
            }

            public xin.vanilla.banira.platform.BaniraConfigHandle handle(Class<?> type) {
                return null;
            }
        };
        xin.vanilla.banira.platform.BaniraPlatform platform =
                (xin.vanilla.banira.platform.BaniraPlatform) java.lang.reflect.Proxy.newProxyInstance(
                        DustbinUiSessionTest.class.getClassLoader(),
                        new Class<?>[]{xin.vanilla.banira.platform.BaniraPlatform.class}, (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "loaderType": return "test";
                                case "minecraftVersion": return "1.16.5";
                                case "isClient": return false;
                                case "isDedicatedServer":
                                case "isDevelopment": return true;
                                case "isModLoaded": return "aotake_sweep".equals(args[0]) || "banira_codex".equals(args[0]);
                                case "modDisplayName": return args[0];
                                case "modIdFromMainClass": return args[0] == xin.vanilla.aotake.AotakeSweep.class
                                        ? "aotake_sweep" : "banira_codex";
                                case "modMainClass": return "aotake_sweep".equals(args[0])
                                        ? xin.vanilla.aotake.AotakeComponent.class : xin.vanilla.banira.BaniraComponent.class;
                                case "configService": return config;
                                case "configDir": return java.nio.file.Paths.get("build", "ui-test-config");
                                default: throw new UnsupportedOperationException(method.toString());
                            }
                        });
        xin.vanilla.banira.platform.BaniraPlatforms.install(platform);
    }

    @Test
    public void bootstrapUsesAnExplicitPlatformWithoutDiscoveringTheLoader() {
        assertTrue(xin.vanilla.banira.platform.BaniraPlatforms.isInstalled());
        assertEquals("test", xin.vanilla.banira.platform.BaniraPlatforms.get().loaderType());
    }

    @Test
    public void sessionBindsTheExistingMenuWithoutReplacingAnySlot() throws Exception {
        ChestContainer menu = ChestContainer.sixRows(17, new PlayerInventory(null), new Inventory(54));
        Object[] slots = menu.slots.toArray();
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.aotake.client.ui.DustbinUiSession");
        } catch (ClassNotFoundException missing) {
            fail("Missing shared container session");
            return;
        }
        Object session = type.getConstructor(net.minecraft.inventory.container.Container.class,
                        boolean.class, java.util.function.BooleanSupplier.class, java.util.function.Consumer.class)
                .newInstance(menu, false, (java.util.function.BooleanSupplier) () -> true,
                        (java.util.function.Consumer<Object>) action -> {
                        });
        assertEquals(true, type.getMethod("matches", net.minecraft.inventory.container.Container.class).invoke(session, menu));
        assertArrayEquals(slots, menu.slots.toArray());
        assertEquals(17, menu.containerId);
    }

    @Test
    public void playerPageUsesTheSameDraftSessionAsNativeFallback() throws Exception {
        try {
            Class.forName("xin.vanilla.aotake.client.ui.AotakePlayerUiSession");
        } catch (ClassNotFoundException missing) {
            fail("Missing player preference draft session");
        }
    }
}
