package xin.vanilla.aotake.data.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.util.JsonUtils;
import xin.vanilla.banira.common.util.PlayerUtils;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分组查看权限：存储于 {@code BANIRA_WORLD_DATA_PATH / MODID / chunk_vault_grants.json}
 */
public final class ChunkVaultGrants {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String FILE_NAME = "chunk_vault_grants.json";

    private static final ConcurrentHashMap<String, Set<String>> CACHE = new ConcurrentHashMap<>();

    /**
     * 已为其从磁盘加载过授权表的服务器实例（{@link BaniraCodex#serverInstance()} 在 ServerStarting 阶段可能尚未绑定，故延迟到 tick）。
     */
    @Nullable
    private static volatile MinecraftServer grantsLoadedForServer;

    private ChunkVaultGrants() {
    }

    /**
     * 在 Banira 已绑定 {@link MinecraftServer} 后调用（例如服务端 tick），安全加载磁盘上的授权表。
     */
    public static void bootstrapWhenServerReady(MinecraftServer server) {
        if (server == null || !server.isRunning()) return;
        if (!BaniraCodex.serverInstance().val() || BaniraCodex.serverInstance().key() != server) return;
        if (grantsLoadedForServer == server) return;
        synchronized (ChunkVaultGrants.class) {
            if (grantsLoadedForServer == server) return;
            load(server);
            grantsLoadedForServer = server;
        }
    }

    @Nullable
    public static Path grantsFileOrNull() {
        if (!BaniraCodex.serverInstance().val()) return null;
        MinecraftServer s = BaniraCodex.serverInstance().key();
        if (s == null) return null;
        return BaniraCodex.BANIRA_WORLD_DATA_PATH.get().resolve(AotakeSweep.MODID).resolve(FILE_NAME);
    }

    public static Path grantsFile() {
        Path p = grantsFileOrNull();
        if (p == null) {
            throw new IllegalStateException("Banira world data path is not available yet");
        }
        return p;
    }

    public static boolean isGranted(ServerPlayerEntity player, String vaultId) {
        bootstrapWhenServerReady(player.getServer());
        Set<String> set = CACHE.get(vaultId);
        if (set == null || set.isEmpty()) return false;
        return set.contains(PlayerUtils.getPlayerUUIDString(player));
    }

    public static void grant(MinecraftServer server, String vaultId, ServerPlayerEntity target) {
        bootstrapWhenServerReady(server);
        String uuid = PlayerUtils.getPlayerUUIDString(target);
        CACHE.computeIfAbsent(vaultId, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        save(server);
    }

    public static void revoke(MinecraftServer server, String vaultId, ServerPlayerEntity target) {
        bootstrapWhenServerReady(server);
        String uuid = PlayerUtils.getPlayerUUIDString(target);
        Set<String> set = CACHE.get(vaultId);
        if (set != null) {
            set.remove(uuid);
        }
        save(server);
    }

    public static void load(MinecraftServer server) {
        CACHE.clear();
        Path path = grantsFileOrNull();
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JsonObject root = JsonUtils.parseObject(text);
            if (root == null || !root.has("grants")) return;
            JsonObject grants = root.getAsJsonObject("grants");
            for (Map.Entry<String, JsonElement> e : grants.entrySet()) {
                String vaultId = e.getKey();
                JsonElement el = e.getValue();
                Set<String> uuids = ConcurrentHashMap.newKeySet();
                if (el.isJsonArray()) {
                    for (JsonElement u : el.getAsJsonArray()) {
                        if (u.isJsonPrimitive()) {
                            uuids.add(u.getAsString());
                        }
                    }
                }
                if (!uuids.isEmpty()) {
                    CACHE.put(vaultId, uuids);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load chunk vault grants: {}", e.getMessage());
        }
    }

    public static void save(MinecraftServer server) {
        Path path = grantsFileOrNull();
        if (path == null) {
            LOGGER.warn("Skip saving chunk vault grants: world data path not ready");
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonObject grants = new JsonObject();
            for (String vaultId : CACHE.keySet()) {
                JsonArray arr = new JsonArray();
                for (String uuid : new HashSet<>(CACHE.getOrDefault(vaultId, Collections.emptySet()))) {
                    arr.add(uuid);
                }
                grants.add(vaultId, arr);
            }
            root.add("grants", grants);
            Files.write(path, JsonUtils.PRETTY_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.warn("Failed to save chunk vault grants: {}", e.getMessage());
        }
    }

    public static Set<String> grantedPlayers(String vaultId) {
        return new HashSet<>(CACHE.getOrDefault(vaultId, Collections.emptySet()));
    }
}
