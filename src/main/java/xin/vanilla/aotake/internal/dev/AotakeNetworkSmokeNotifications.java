package xin.vanilla.aotake.internal.dev;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.fml.ModList;
import xin.vanilla.aotake.AotakeComponent;
import xin.vanilla.aotake.enums.EnumCommandType;
import xin.vanilla.aotake.util.AotakeUtils;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.notification.NotificationBudget;
import xin.vanilla.banira.common.util.MessageUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Bounded typed messages for the dedicated network smoke, not gameplay notifications. */
public final class AotakeNetworkSmokeNotifications {
    public static final int EXPLICIT_COLOR = 0xFFFFAA00;
    private static boolean sent;

    private AotakeNetworkSmokeNotifications() { }

    public static String token(String type, String phase) {
        return "notification-route:" + phase + ":" + type + ": ";
    }

    public static Component payload(String type, String phase) {
        return AotakeComponent.get().literal(token(type, phase)).append(
                AotakeComponent.get().literal("detail").color(EXPLICIT_COLOR)
                        .clickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/aotake help"))
                        .hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new StringTextComponent("full detail"))));
    }

    public static boolean sendWhenReady(ServerPlayerEntity player) {
        if (!AotakeNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke is disabled");
        if (sent) return true;
        if (!PlayerUtils.isRemoteClientModInstalled(player, BaniraCodex.MODID)) return false;
        recordRuntime();
        for (String type : AotakeNotificationTypes.ALL_TYPE_IDS) {
            MessageUtils.sendNotification(player, payload(type, AotakeNetworkSmokeStatus.phase()), type);
        }
        for (String command : new String[]{AotakeUtils.getCommand(EnumCommandType.HELP),
                AotakeUtils.getCommand(EnumCommandType.CHUNK_VAULT) + " list"}) {
            int result = player.getServer().getCommands().performCommand(
                    player.createCommandSourceStack().withPermission(4), command);
            if (result <= 0) throw new IllegalStateException("Notification command failed: " + command);
        }
        AotakeNetworkSmokeStatus.append("PASS notification-production-commands help vault-list");
        sent = true;
        AotakeNetworkSmokeStatus.append("PASS notification-routes-submitted types=" + AotakeNotificationTypes.ALL_TYPE_IDS.length);
        return true;
    }

    public static void recordRuntime() {
        if (!AotakeNetworkSmokeStatus.enabled()) throw new IllegalStateException("Smoke is disabled");
        try {
            Path path = ModList.get().getModFileById(BaniraCodex.MODID).getFile().getFilePath().toAbsolutePath();
            java.net.URL owner = BaniraCodex.class.getProtectionDomain().getCodeSource().getLocation();
            for (Class<?> type : new Class<?>[]{Component.class, MessageUtils.class, NotificationBudget.class}) {
                java.net.URL source = type.getProtectionDomain().getCodeSource().getLocation();
                if (!owner.equals(source)) throw new IllegalStateException("Banira class source mismatch: " + source);
                byte[] bytes = Files.readAllBytes(path);
                StringBuilder hash = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) hash.append(String.format("%02x", b & 255));
                AotakeNetworkSmokeStatus.append("RUNTIME " + type.getName() + " sha256=" + hash + " size=" + bytes.length + " path=" + path + " source=" + source);
            }
        } catch (Exception error) { throw new IllegalStateException("Cannot fingerprint loaded Banira", error); }
    }
}
