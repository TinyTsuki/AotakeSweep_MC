package xin.vanilla.aotake.notification;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AotakeNotificationTypeMetadataContractTest {

    @Test
    public void clientRegistersLocalizedMetadataForEveryNotificationType() throws Exception {
        String source = read("src/main/java/xin/vanilla/aotake/event/ClientModEventHandler.java");
        String zh = read("src/main/resources/assets/aotake_sweep/lang/zh_cn.json");
        String en = read("src/main/resources/assets/aotake_sweep/lang/en_us.json");
        String[] keys = {
                "notification_type_sweep_countdown",
                "notification_type_sweep_result_interactive",
                "notification_type_sweep_result_compact",
                "notification_type_chunk_check_interactive",
                "notification_type_chunk_check_compact",
                "notification_type_entity_tool_feedback",
                "notification_type_dustbin",
                "notification_type_chunk_vault_list",
                "notification_type_admin_broadcast",
                "notification_type_player_preference",
                "notification_type_help"
        };

        assertTrue(source.contains("BaniraClientNotificationTypes.registerModDisplayName("));
        assertTrue(source.contains("\"mod_name\""));
        assertFalse(source.contains("client.notification.NotificationTypeRegistry"));
        for (String key : keys) {
            assertTrue("Missing client registration for " + key, source.contains("\"" + key + "\""));
            assertTrue("Missing zh_cn translation for " + key,
                    zh.contains("\"word.aotake_sweep." + key + "\""));
            assertTrue("Missing en_us translation for " + key,
                    en.contains("\"word.aotake_sweep." + key + "\""));
        }
        String help = read("src/main/java/xin/vanilla/aotake/command/impl/HelpCommand.java");
        assertTrue(help.contains("MessageUtils.sendNotification(player, helpInfo, AotakeNotificationTypes.HELP)"));

        String config = read("src/main/java/xin/vanilla/aotake/command/impl/ConfigCommand.java");
        assertTrue(config.contains("MessageUtils.sendMessage(source, true,"));
        assertFalse(config.contains("source.sendSuccess("));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
