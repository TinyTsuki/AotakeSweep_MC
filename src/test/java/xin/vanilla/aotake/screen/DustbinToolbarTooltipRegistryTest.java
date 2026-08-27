package xin.vanilla.aotake.screen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DustbinToolbarTooltipRegistryTest {

    @Test
    public void returnsTopmostTooltipAtPointerAndClearsWithScreen() {
        DustbinToolbarTooltipRegistry<String> registry = new DustbinToolbarTooltipRegistry<>();
        registry.add(10, 20, 20, 20, "first");
        registry.add(20, 20, 20, 20, "topmost");

        assertEquals("first", registry.find(15, 25));
        assertEquals("topmost", registry.find(25, 25));
        assertNull(registry.find(45, 25));

        registry.clear();
        assertNull(registry.find(15, 25));
    }
}
