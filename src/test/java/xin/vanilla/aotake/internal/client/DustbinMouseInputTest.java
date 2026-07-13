package xin.vanilla.aotake.internal.client;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DustbinMouseInputTest {
    @Test
    public void firesOnceWhenClickStartsAndEndsInsideButton() {
        DustbinMouseInput input = new DustbinMouseInput();
        input.update(12, 12, false);
        input.update(12, 12, true);

        assertTrue(input.isPressingLeftEx());
        assertTrue(input.isHoverInRect(10, 10, 10, 10));

        input.update(12, 12, false);
        assertTrue(input.isLeftPressedInRect(10, 10, 10, 10));

        input.update(12, 12, false);
        assertFalse(input.isLeftPressedInRect(10, 10, 10, 10));
    }

    @Test
    public void doesNotFireWhenPointerIsReleasedOutsideButton() {
        DustbinMouseInput input = new DustbinMouseInput();
        input.update(12, 12, true);
        input.update(30, 30, false);

        assertFalse(input.isLeftPressedInRect(10, 10, 10, 10));
    }
}
