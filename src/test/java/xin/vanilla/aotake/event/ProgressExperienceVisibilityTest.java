package xin.vanilla.aotake.event;

import org.junit.Test;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ProgressExperienceVisibilityTest {
    private boolean hides(boolean held, List<EnumProgressBarType> modes, boolean leaf, boolean pole, boolean text) throws Exception {
        Method method;
        try {
            method = ClientGameEventHandler.class.getDeclaredMethod("hidesExperience", boolean.class,
                    List.class, boolean.class, boolean.class, boolean.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Missing configured experience visibility policy", missing);
        }
        method.setAccessible(true);
        return (Boolean) method.invoke(null, held, modes, leaf, pole, text);
    }

    @Test public void normalModeKeepsExperienceByDefault() throws Exception {
        assertFalse(hides(false, Arrays.asList(EnumProgressBarType.values()), false, false, false));
    }

    @Test public void eachSelectedDisplayHonorsItsHideFlag() throws Exception {
        assertTrue(hides(false, Arrays.asList(EnumProgressBarType.LEAF), true, false, false));
        assertTrue(hides(false, Arrays.asList(EnumProgressBarType.POLE), false, true, false));
        assertTrue(hides(false, Arrays.asList(EnumProgressBarType.TEXT), false, false, true));
    }

    @Test public void unselectedFlagsDoNotHideExperience() throws Exception {
        assertFalse(hides(false, Arrays.asList(EnumProgressBarType.LEAF), false, true, true));
        assertFalse(hides(false, Collections.emptyList(), true, true, true));
    }

    @Test public void heldOrToggledProgressKeepsExistingExperienceOwnership() throws Exception {
        assertTrue(hides(true, Collections.emptyList(), false, false, false));
    }

    @Test public void combinedDisplaysHideWhenAnySelectedFlagIsEnabled() throws Exception {
        assertTrue(hides(false, Arrays.asList(EnumProgressBarType.LEAF, EnumProgressBarType.TEXT), false, false, true));
    }
}
