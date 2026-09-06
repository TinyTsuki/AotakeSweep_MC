package xin.vanilla.aotake.internal.client.dev;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNetworkSmokeClientRunnerTest {
    @Test
    public void acceptsOnlyExactPhaseAndReadinessMarkers() {
        AotakeNetworkSmokeClientRunner.ServerSignals signals = new AotakeNetworkSmokeClientRunner.ServerSignals();
        signals.accept(Arrays.asList("PASS sustained-ready-extra", "FINISHED phase-one-extra",
                "FINISHED phase-two", "PASS final-checkpoint-extra"), "phase-one");
        assertFalse(signals.sustainedReady);
        assertFalse(signals.finished);
        assertFalse(signals.checkpointReady);
        signals.accept(Arrays.asList("PASS sustained-ready", "PASS final-checkpoint", "FINISHED phase-one"), "phase-one");
        assertTrue(signals.sustainedReady);
        assertTrue(signals.checkpointReady);
        assertTrue(signals.finished);
    }

    @Test(expected = IllegalStateException.class)
    public void propagatesServerFailureEvenAfterFinishedMarker() {
        AotakeNetworkSmokeClientRunner.ServerSignals signals = new AotakeNetworkSmokeClientRunner.ServerSignals();
        signals.accept(Arrays.asList("FINISHED phase-one", "FAIL server workload expired"), "phase-one");
    }

    @Test
    public void incompleteWritesDoNotErasePreviouslyObservedReadiness() {
        AotakeNetworkSmokeClientRunner.ServerSignals signals = new AotakeNetworkSmokeClientRunner.ServerSignals();
        signals.accept(Collections.singletonList("PASS sustained-ready"), "phase-one");
        signals.accept(Collections.<String>emptyList(), "phase-one");
        assertTrue(signals.sustainedReady);
    }

    @Test
    public void acceptsCheckpointDetailsWithoutClaimingClientCacheCounts() {
        AotakeNetworkSmokeClientRunner.ServerSignals signals = new AotakeNetworkSmokeClientRunner.ServerSignals();
        signals.accept(Collections.singletonList("PASS final-checkpoint cycles=20 paper=40000 captured=20"), "phase-one");
        assertTrue(signals.checkpointReady);
        assertFalse(signals.finished);
    }
}
