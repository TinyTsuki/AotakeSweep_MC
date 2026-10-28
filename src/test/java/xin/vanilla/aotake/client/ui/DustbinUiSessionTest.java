package xin.vanilla.aotake.client.ui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class DustbinUiSessionTest {
    @BeforeClass
    public static void bootstrap() {
        try {
            xin.vanilla.aotake.rule.NativeTestRegistries.bootstrap();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    public void sessionBindsTheExistingMenuWithoutReplacingAnySlot() throws Exception {
        ChestMenu menu = ChestMenu.sixRows(17, new Inventory(null), new SimpleContainer(54));
        Object[] slots = menu.slots.toArray();
        Class<?> type;
        try {
            type = Class.forName("xin.vanilla.aotake.client.ui.DustbinUiSession");
        } catch (ClassNotFoundException missing) {
            fail("Missing shared container session");
            return;
        }
        Object session = type.getConstructor(net.minecraft.world.inventory.AbstractContainerMenu.class,
                        boolean.class, java.util.function.BooleanSupplier.class, java.util.function.Consumer.class)
                .newInstance(menu, false, (java.util.function.BooleanSupplier) () -> true,
                        (java.util.function.Consumer<Object>) action -> {
                        });
        assertEquals(true, type.getMethod("matches", net.minecraft.world.inventory.AbstractContainerMenu.class).invoke(session, menu));
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
