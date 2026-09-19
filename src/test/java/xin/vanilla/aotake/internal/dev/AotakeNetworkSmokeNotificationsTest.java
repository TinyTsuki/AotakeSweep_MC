package xin.vanilla.aotake.internal.dev;

import org.junit.Test;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;
import xin.vanilla.banira.common.notification.ServerNotificationTypeRegistry;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

public class AotakeNetworkSmokeNotificationsTest {
    @Test
    public void everyRegisteredTypeHasAnIndependentPhasePayload() {
        assertEquals(11, AotakeNotificationTypes.ALL_TYPE_IDS.length);
        assertEquals(11, new HashSet<>(Arrays.asList(AotakeNotificationTypes.ALL_TYPE_IDS)).size());
        AotakeNotificationTypes.registerAllOnServer();
        for (String type : AotakeNotificationTypes.ALL_TYPE_IDS) {
            assertTrue(ServerNotificationTypeRegistry.sortedSnapshot().contains(type));
            Component first = AotakeNetworkSmokeNotifications.payload(type, "phase-one");
            Component second = AotakeNetworkSmokeNotifications.payload(type, "phase-two");
            assertNotEquals(first.getString("en_us"), second.getString("en_us"));
            assertEquals(AotakeNetworkSmokeNotifications.EXPLICIT_COLOR, first.getChildren().get(0).color().argb());
            assertEquals("/aotake help", first.getChildren().get(0).clickEvent().getValue());
            assertNotNull(first.getChildren().get(0).hoverEvent());
            assertEquals(first.toJson(), AotakeNetworkSmokeNotifications.payload(type, "phase-one").toJson());
        }
    }

    @Test
    public void interactiveResultsAndHelpRemainChatWhileCountdownRemainsOverlay() {
        assertEquals(EnumNotificationTypeDisplayMode.VANILLA_CHAT, AotakeNotificationTypes.defaultDisplay(AotakeNotificationTypes.HELP));
        assertEquals(EnumNotificationTypeDisplayMode.VANILLA_CHAT, AotakeNotificationTypes.defaultDisplay(AotakeNotificationTypes.SWEEP_RESULT_INTERACTIVE));
        assertEquals(EnumNotificationTypeDisplayMode.VANILLA_CHAT, AotakeNotificationTypes.defaultDisplay(AotakeNotificationTypes.CHUNK_CHECK_INTERACTIVE));
        assertEquals(EnumNotificationTypeDisplayMode.OVERLAY, AotakeNotificationTypes.defaultDisplay(AotakeNotificationTypes.SWEEP_COUNTDOWN));
    }
}
