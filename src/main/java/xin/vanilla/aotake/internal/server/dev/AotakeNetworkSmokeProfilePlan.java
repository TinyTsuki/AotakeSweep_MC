package xin.vanilla.aotake.internal.server.dev;

/** Keeps the development cleanup workload alive for the full profiler window. */
final class AotakeNetworkSmokeProfilePlan {
    static final int REQUIRED_CYCLES = 20;
    private AotakeNetworkSmokeProfilePlan() {
    }

    static boolean shouldContinue(boolean sparkReportWritten, int completedCleanupCycles) {
        if (sparkReportWritten && completedCleanupCycles < REQUIRED_CYCLES) {
            throw new IllegalStateException("Sampling ended before all cleanup cycles completed");
        }
        return !sparkReportWritten;
    }
}
