package xin.vanilla.aotake.client.minimap;

import net.minecraft.network.chat.Component;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.config.ClientConfig;
import xin.vanilla.banira.api.client.hud.BaniraMinimapLines;
import xin.vanilla.banira.api.client.hud.MinimapLine;
import xin.vanilla.banira.common.network.BaniraNetworkContext;

public final class AotakeMinimapCountdown {
    private static final SweepCountdownConnection CONNECTION = new SweepCountdownConnection(System::nanoTime, () -> {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() == null ? null : mc.getConnection().getConnection();
    });

    private AotakeMinimapCountdown() {
    }

    public static void register() {
        BaniraMinimapLines.register(new MinimapLine(ResourceLocation.fromNamespaceAndPath("aotake_sweep", "cleanup_countdown"),
                net.minecraft.network.chat.Component.translatable("word.aotake_sweep.minimap_cleanup_countdown"), () -> {
                    if (!ClientConfig.get().minimapCountdown().enabled()) return net.minecraft.network.chat.Component.empty();
                    String time = CONNECTION.formattedTime();
                    return time.isEmpty() ? net.minecraft.network.chat.Component.empty()
                            : net.minecraft.network.chat.Component.literal(AotakeComponent.get().transClientAuto("minimap_cleanup_countdown", time).toString());
                }, () -> ClientConfig.get().minimapCountdown().host().notificationHost(), 200));
    }

    public static void beginConnection() {
        CONNECTION.beginConnection();
    }

    public static void accept(BaniraNetworkContext context, long now, long deadline, long interval, Runnable accepted) {
        CONNECTION.accept(context, now, deadline, interval, accepted);
    }

    public static void reset() {
        CONNECTION.reset();
    }
}
