package xin.vanilla.aotake.internal.client.dev;

/** Waits for a completed play connection before the smoke sends custom C2S packets. */
final class AotakeNetworkSmokeClientPlan {
    private static final int CUSTOM_CHANNEL_SETTLE_TICKS = 20;

    private AotakeNetworkSmokeClientPlan() {
    }

    static boolean isCustomChannelReady(int ticksSinceLogin, boolean remoteModConfirmed) {
        return remoteModConfirmed && ticksSinceLogin >= CUSTOM_CHANNEL_SETTLE_TICKS;
    }
}
