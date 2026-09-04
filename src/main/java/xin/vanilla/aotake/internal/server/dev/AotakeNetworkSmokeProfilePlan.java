package xin.vanilla.aotake.internal.server.dev;

/** Keeps the development cleanup workload alive for the full profiler window. */
final class AotakeNetworkSmokeProfilePlan {
    private AotakeNetworkSmokeProfilePlan() {
    }

    static boolean shouldContinue(boolean sparkReportWritten, int completedCleanupCycles) {
        return !sparkReportWritten || completedCleanupCycles < 1;
    }
}
