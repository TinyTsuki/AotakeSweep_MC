package xin.vanilla.aotake.internal.server.dev;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeWorkloadTest {
    @Test
    public void completesAfterTheFixedGameplayWindow() {
        AotakeNetworkSmokeWorkload workload = new AotakeNetworkSmokeWorkload(50);

        assertFalse(workload.completeAt(369));
        assertTrue(workload.completeAt(370));
    }
}
