package xin.vanilla.aotake.internal.server.dev;

/** 网络 smoke 服务端受控关停的纯状态决策。 */
final class AotakeNetworkSmokeServerPlan {
    private AotakeNetworkSmokeServerPlan() {
    }

    static boolean mayShutdown(String phase, boolean finished, boolean sparkReportWritten) {
        return finished && (!"phase-one".equals(phase) || sparkReportWritten);
    }
}
