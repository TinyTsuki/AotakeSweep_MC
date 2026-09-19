package xin.vanilla.aotake.internal.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeNotifications;
import xin.vanilla.aotake.internal.dev.AotakeNetworkSmokeStatus;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.api.client.notification.BaniraClientNotificationTypes;
import xin.vanilla.banira.api.client.theme.BaniraThemes;
import xin.vanilla.banira.client.data.BaniraColorConfig;
import xin.vanilla.banira.client.data.NotificationLogEntry;
import xin.vanilla.banira.client.gui.component.Notification;
import xin.vanilla.banira.client.notification.NotificationTypeSettingsStore;
import xin.vanilla.banira.client.util.NotificationManager;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.data.NotificationData;
import xin.vanilla.banira.common.enums.EnumMoveType;
import xin.vanilla.banira.common.enums.EnumNotificationStyle;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;
import xin.vanilla.banira.common.enums.EnumPosition;

import java.util.List;

/** Checks real received messages separately from client factory palette checks. */
final class AotakeNetworkSmokeNotificationsCheck {
    private final long startedAt = System.currentTimeMillis();
    private boolean verified;

    boolean verifyWhenReady(Minecraft client) {
        if (verified) return true;
        String phase = AotakeNetworkSmokeStatus.phase();
        List<NotificationLogEntry> entries = NotificationManager.get().getLog();
        int chats = 0;
        for (String type : AotakeNotificationTypes.ALL_TYPE_IDS) {
            Component expected = AotakeNetworkSmokeNotifications.payload(type, phase);
            NotificationLogEntry entry = entries.stream().filter(e -> "network".equals(e.source())
                    && type.equals(e.notificationType()) && expected.text().equals(e.component().text())).findFirst().orElse(null);
            if (entry == null) return false;
            Component received = entry.component();
            require(received.getChildren().size() == 1, "nested content " + type);
            Component detail = received.getChildren().get(0);
            Component expectedDetail = expected.getChildren().get(0);
            require(detail.color().argb() == expectedDetail.color().argb(), "explicit color " + type);
            require(expectedDetail.clickEvent().equals(detail.clickEvent()), "click " + type);
            require(expectedDetail.hoverEvent().equals(detail.hoverEvent()), "hover " + type);
            require(BaniraClientNotificationTypes.tooltip(type) != null, "description " + type);
            EnumNotificationTypeDisplayMode mode = AotakeNotificationTypes.defaultDisplay(type);
            require(BaniraClientNotificationTypes.displayDefault(type) == mode, "registry default " + type);
            require(NotificationTypeSettingsStore.get().getOrCreate(type).displayMode() == mode, "resolved default " + type);
            boolean inChat = chatContains(client, expected.text());
            require(inChat == (mode == EnumNotificationTypeDisplayMode.VANILLA_CHAT), "chat destination " + type);
            if (inChat) {
                chats++;
                require(received.color().argb() == expected.color().argb(), "vanilla foreground changed " + type);
            }
            verifyFactory(type, expected);
        }
        for (String type : new String[]{AotakeNotificationTypes.HELP, AotakeNotificationTypes.CHUNK_VAULT_LIST}) {
            boolean received = entries.stream().anyMatch(e -> "network".equals(e.source())
                    && type.equals(e.notificationType()) && e.timestamp() >= startedAt
                    && !e.component().text().startsWith("notification-route:"));
            if (!received) return false;
        }
        AotakeNetworkSmokeStatus.append("PASS notification-production-received help vault-list");
        verified = true;
        AotakeNetworkSmokeNotifications.recordRuntime();
        AotakeNetworkSmokeStatus.append("PASS notification-routes-client types=11 chat=" + chats + " configured-overlay=" + (11 - chats)
                + " chat-excluded=" + (11 - chats) + " metadata=preserved theme-factory-cases=132 live-overlay-queue=not-inspected");
        return true;
    }

    private static boolean chatContains(Minecraft client, String token) {
        // Minecraft exposes no full chat snapshot; this observer never changes the chat buffer.
        List<GuiMessage> lines = ObfuscationReflectionHelper.getPrivateValue(
                ChatComponent.class, client.gui.getChat(), "f_93760_");
        if (lines == null) throw new IllegalStateException("Missing Minecraft chat buffer");
        return lines.stream().anyMatch(line -> line.content().getString().contains(token));
    }

    private static void verifyFactory(String type, Component expected) {
        NotificationTypeSettingsStore.TypeSettings setting = NotificationTypeSettingsStore.get().getOrCreate(type);
        EnumNotificationTypeDisplayMode previous = setting.displayMode();
        BaniraColorConfig theme = BaniraColorConfig.forSeason(BaniraThemes.seasonFor(AotakeSweep.MODID));
        try {
            for (EnumNotificationTypeDisplayMode mode : EnumNotificationTypeDisplayMode.values()) {
                setting.displayMode(mode);
                for (EnumNotificationStyle style : EnumNotificationStyle.values()) {
                    NotificationData data = NotificationData.of(expected.clone(), EnumPosition.TOP_CENTER, EnumMoveType.AUTO, 3000L, style, type);
                    Notification result = Notification.fromData(data, true);
                    int bg = data.bgColor().argb();
                    int border = data.borderColor().argb();
                    int text = expected.color().argb();
                    if (mode == EnumNotificationTypeDisplayMode.OVERLAY) {
                        switch (style) {
                            case SUCCESS: bg = theme.notificationSuccessBg(); border = theme.notificationSuccessBorder(); text = theme.notificationSuccessText(); break;
                            case WARNING: bg = theme.notificationWarningBg(); border = theme.notificationWarningBorder(); text = theme.notificationWarningText(); break;
                            case ERROR: bg = theme.notificationErrorBg(); border = theme.notificationErrorBorder(); text = theme.notificationErrorText(); break;
                            default: bg = theme.notificationNormalBg(); border = theme.notificationNormalBorder(); text = theme.notificationNormalText();
                        }
                    }
                    require(result.component().color().argb() == text, "factory foreground " + type + "/" + mode + "/" + style);
                    require(result.bgColor().argb() == bg && result.borderColor().argb() == border, "factory palette " + type + "/" + mode + "/" + style);
                    require(result.component().getChildren().get(0).color().argb() == AotakeNetworkSmokeNotifications.EXPLICIT_COLOR, "factory semantic color " + type);
                }
            }
        } finally { setting.displayMode(previous); }
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException("Notification regression: " + reason);
    }
}
