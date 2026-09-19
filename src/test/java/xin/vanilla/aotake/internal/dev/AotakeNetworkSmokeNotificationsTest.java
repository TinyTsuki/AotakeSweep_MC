package xin.vanilla.aotake.internal.dev;

import org.junit.BeforeClass;
import org.junit.Test;
import xin.vanilla.aotake.notification.AotakeNotificationTypes;
import xin.vanilla.banira.common.data.Component;
import xin.vanilla.banira.common.enums.EnumNotificationTypeDisplayMode;
import xin.vanilla.banira.common.notification.ServerNotificationTypeRegistry;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

public class AotakeNetworkSmokeNotificationsTest {
    @BeforeClass
    public static void bootstrapGame() throws Exception {
        // Forge 52 hover codecs initialize item components through the mod bus.
        var loading = net.minecraftforge.fml.loading.LoadingModList.of(
                java.util.List.of(), java.util.List.of(), null);
        loading.setBrokenFiles(java.util.List.of());
        setField(net.minecraftforge.fml.loading.FMLLoader.class, "loadingModList", loading);
        cpw.mods.modlauncher.api.IModuleLayerManager layers = layer -> java.util.Optional.of(ModuleLayer.boot());
        setField(net.minecraftforge.fml.loading.FMLLoader.class, "moduleLayerManager", layers);
        var mods = net.minecraftforge.fml.ModList.of(java.util.List.of(), java.util.List.of());
        var setLoadedMods = net.minecraftforge.fml.ModList.class.getDeclaredMethod("setLoadedMods", java.util.List.class);
        setLoadedMods.setAccessible(true);
        setLoadedMods.invoke(mods, java.util.List.of());
        net.minecraftforge.fml.ModLoader.get();
        setField(net.minecraftforge.fml.ModLoader.class, "loadingStateValid", true);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static void setField(Class<?> type, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

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
