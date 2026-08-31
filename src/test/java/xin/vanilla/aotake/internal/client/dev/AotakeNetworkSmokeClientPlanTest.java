package xin.vanilla.aotake.internal.client.dev;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeClientPlanTest {
    @Test
    public void firstPhaseSendsThePreferenceRoundTrip() {
        assertEquals(AotakeNetworkSmokeClientPlan.LoginAction.SEND_PLAYER_CONFIG,
                AotakeNetworkSmokeClientPlan.afterLogin("phase-one", true, true));
    }

    @Test
    public void secondPhaseOpensThePersistedDustbinOnlyAfterPreferencesArrive() {
        assertEquals(AotakeNetworkSmokeClientPlan.LoginAction.OPEN_DUSTBIN,
                AotakeNetworkSmokeClientPlan.afterLogin("phase-two", false, false));
    }

    @Test(expected = IllegalStateException.class)
    public void secondPhaseRejectsUnexpectedPersistedPreferences() {
        AotakeNetworkSmokeClientPlan.afterLogin("phase-two", true, false);
    }

    @Test
    public void waitsForTheMatchingServerPhaseBeforeDisconnecting() {
        assertFalse(AotakeNetworkSmokeClientPlan.serverFinished("FINISHED phase-one", "phase-two"));
        assertTrue(AotakeNetworkSmokeClientPlan.serverFinished("PASS phase-one\nFINISHED phase-two\n", "phase-two"));
    }
}
