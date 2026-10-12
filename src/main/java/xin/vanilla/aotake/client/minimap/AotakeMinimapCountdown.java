package xin.vanilla.aotake.client.minimap;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
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
        BaniraMinimapLines.register(new MinimapLine(new ResourceLocation("aotake_sweep", "cleanup_countdown"),
                new TranslatableComponent("word.aotake_sweep.minimap_cleanup_countdown"), () -> {
                    if (!ClientConfig.get().minimapCountdown().enabled()) return TextComponent.EMPTY;
                    String time = CONNECTION.formattedTime();
                    return time.isEmpty() ? TextComponent.EMPTY
                            : new TextComponent(AotakeComponent.get().transClientAuto("minimap_cleanup_countdown", time).toString());
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
