package xin.vanilla.aotake.internal.server.dev;

/** Keeps the development cleanup workload alive for the full profiler window. */
final class AotakeNetworkSmokeProfilePlan {
    static final int REQUIRED_CYCLES = 20;
    private AotakeNetworkSmokeProfilePlan() {
    }

    static boolean canStartSampling(boolean chunksReady, int waitedTicks) {
        if (!chunksReady && waitedTicks > 200) {
            throw new IllegalStateException("Measured chunks did not become ready");
        }
        return chunksReady;
    }

    static boolean shouldContinue(boolean sparkReportWritten, int completedCleanupCycles) {
        if (sparkReportWritten && completedCleanupCycles < REQUIRED_CYCLES) {
            throw new IllegalStateException("Sampling ended before all cleanup cycles completed");
        }
        return !sparkReportWritten;
    }
}
