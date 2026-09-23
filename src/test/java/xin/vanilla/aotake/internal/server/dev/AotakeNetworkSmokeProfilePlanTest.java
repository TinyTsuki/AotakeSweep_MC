package xin.vanilla.aotake.internal.server.dev;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AotakeNetworkSmokeProfilePlanTest {

    @Test
    public void samplerWaitsForEntityChunksWithinTheBound() {
        for (int waited = 0; waited <= 200; waited++) {
            assertFalse(AotakeNetworkSmokeProfilePlan.canStartSampling(false, waited));
        }
    }

    @Test
    public void readyEntityChunksAllowSamplingImmediately() {
        assertTrue(AotakeNetworkSmokeProfilePlan.canStartSampling(true, 0));
        assertTrue(AotakeNetworkSmokeProfilePlan.canStartSampling(true, 200));
    }

    @Test(expected = IllegalStateException.class)
    public void unavailableEntityChunksCannotWaitForever() {
        AotakeNetworkSmokeProfilePlan.canStartSampling(false, 201);
    }

    @Test
    public void keepsTheCleanupWorkloadAliveUntilSparkWritesItsReport() {
        assertTrue(AotakeNetworkSmokeProfilePlan.shouldContinue(false, 0));
        assertTrue(AotakeNetworkSmokeProfilePlan.shouldContinue(false, 3));
        assertFalse(AotakeNetworkSmokeProfilePlan.shouldContinue(true, 20));
    }

    @Test
    public void expiredSamplingCannotBeRepairedByUnsampledWork() {
        for (int completed : new int[]{0, 1, 19}) {
            try {
                AotakeNetworkSmokeProfilePlan.shouldContinue(true, completed);
                fail("Incomplete cycles accepted after sampling ended: " + completed);
            } catch (IllegalStateException expected) {
            }
        }
    }
}
