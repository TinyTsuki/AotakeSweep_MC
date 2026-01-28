package xin.vanilla.aotake.util;


import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLanguageManager {
    private static final Map<UUID, String> languageMap = new ConcurrentHashMap<>();

    public static String get(ServerPlayer player) {
        return get(player.getUUID());
    }

    public static String get(UUID uuid) {
        return languageMap.getOrDefault(uuid, "en_us");
    }

    public static void set(ServerPlayer player, String language) {
        set(player.getUUID(), language);
    }

    public static void set(UUID uuid, String language) {
        languageMap.put(uuid, language);
    }

    public static void remove(ServerPlayer player) {
        remove(player.getUUID());
    }

    public static void remove(UUID uuid) {
        languageMap.remove(uuid);
    }

    public static void clear() {
        languageMap.clear();
    }
}
