package xin.vanilla.aotake.internal.server.dev;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeProfilePlanTest {

    @Test
    public void keepsTheCleanupWorkloadAliveUntilSparkWritesItsReport() {
        assertTrue(AotakeNetworkSmokeProfilePlan.shouldContinue(false, 0));
        assertTrue(AotakeNetworkSmokeProfilePlan.shouldContinue(false, 3));
        assertTrue(AotakeNetworkSmokeProfilePlan.shouldContinue(true, 0));
        assertFalse(AotakeNetworkSmokeProfilePlan.shouldContinue(true, 1));
    }
}
