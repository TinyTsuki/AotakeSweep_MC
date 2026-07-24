package xin.vanilla.aotake.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DustbinPageNavigationTest {
    @Test
    public void rejectsNavigationOutsideAvailablePages() {
        assertEquals(-1, DustbinPageNavigation.targetPage(1, 3, -1));
        assertEquals(-1, DustbinPageNavigation.targetPage(3, 3, 1));
        assertFalse(DustbinPageNavigation.canNavigate(1, 3, -1));
        assertFalse(DustbinPageNavigation.canNavigate(3, 3, 1));
    }

    @Test
    public void acceptsRefreshAndValidNavigation() {
        assertEquals(1, DustbinPageNavigation.targetPage(1, 3, 0));
        assertEquals(2, DustbinPageNavigation.targetPage(1, 3, 1));
        assertTrue(DustbinPageNavigation.canNavigate(2, 3, -1));
    }
}
