package xin.vanilla.aotake.internal.client.dev;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeClientPlanTest {
    @Test
    public void waitsForTheCustomChannelToSettleBeforeSendingClientPackets() {
        assertFalse(AotakeNetworkSmokeClientPlan.isCustomChannelReady(0, true));
        assertFalse(AotakeNetworkSmokeClientPlan.isCustomChannelReady(19, true));
        assertFalse(AotakeNetworkSmokeClientPlan.isCustomChannelReady(20, false));
        assertTrue(AotakeNetworkSmokeClientPlan.isCustomChannelReady(20, true));
    }
}
