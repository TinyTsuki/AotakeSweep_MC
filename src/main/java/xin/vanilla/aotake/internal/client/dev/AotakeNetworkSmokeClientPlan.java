package xin.vanilla.aotake.internal.client.dev;

/** 网络 smoke 客户端在登录同步后的纯状态决策。 */
final class AotakeNetworkSmokeClientPlan {
    private AotakeNetworkSmokeClientPlan() {
    }

    static LoginAction afterLogin(String phase, boolean showSweepResult, boolean enableWarningVoice) {
        if ("phase-one".equals(phase)) {
            return LoginAction.SEND_PLAYER_CONFIG;
        }
        if ("phase-two".equals(phase)) {
            if (showSweepResult || enableWarningVoice) {
                throw new IllegalStateException("Persisted player preferences were not synchronized");
            }
            return LoginAction.OPEN_DUSTBIN;
        }
        throw new IllegalStateException("Unknown network smoke phase: " + phase);
    }

    static boolean serverFinished(String status, String phase) {
        return status != null && status.contains("FINISHED " + phase);
    }

    enum LoginAction {
        SEND_PLAYER_CONFIG,
        OPEN_DUSTBIN
    }
}
