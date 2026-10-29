package xin.vanilla.aotake.event;

import org.junit.Test;
import xin.vanilla.aotake.enums.EnumProgressBarType;
import xin.vanilla.aotake.client.ui.SweepProgressUiBinding;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ProgressExperienceVisibilityTest {
    private boolean hides(boolean held, List<EnumProgressBarType> modes, boolean leaf, boolean pole, boolean text) throws Exception {
        return SweepProgressUiBinding.selection(held ? List.of() : modes, held ? modes : List.of(),
                held, false, leaf, pole, text).hideExperience();
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

    @Test public void heldOrToggledProgressHonorsSelectedModesAndHideFlags() throws Exception {
        assertFalse(hides(true, Collections.emptyList(), false, false, false));
        assertFalse(hides(true, List.of(EnumProgressBarType.LEAF), false, false, false));
        assertTrue(hides(true, List.of(EnumProgressBarType.LEAF), true, false, false));
    }

    @Test public void combinedDisplaysHideWhenAnySelectedFlagIsEnabled() throws Exception {
        assertTrue(hides(false, Arrays.asList(EnumProgressBarType.LEAF, EnumProgressBarType.TEXT), false, false, true));
    }
}
