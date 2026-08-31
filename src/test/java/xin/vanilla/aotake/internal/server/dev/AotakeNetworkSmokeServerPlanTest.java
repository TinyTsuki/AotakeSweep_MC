package xin.vanilla.aotake.internal.server.dev;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeServerPlanTest {
    @Test
    public void writePhaseWaitsForTheNativeSparkReportBeforeShutdown() {
        assertFalse(AotakeNetworkSmokeServerPlan.mayShutdown("phase-one", true, false));
        assertTrue(AotakeNetworkSmokeServerPlan.mayShutdown("phase-one", true, true));
    }

    @Test
    public void restorePhaseDoesNotRequireAProfilerReport() {
        assertTrue(AotakeNetworkSmokeServerPlan.mayShutdown("phase-two", true, false));
    }
}
