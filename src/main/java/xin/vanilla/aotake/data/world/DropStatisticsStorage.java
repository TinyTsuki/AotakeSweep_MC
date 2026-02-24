package xin.vanilla.aotake.data.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.aotake.util.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 掉落物统计
 */
public class DropStatisticsStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 获取当日统计文件的存储路径
     */
    public static Path getStatsDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(AotakeSweep.MODID);
    }

    /**
     * 获取指定日期的统计文件路径
     */
    public static Path getStatsFile(MinecraftServer server, String dateStr) {
        return getStatsDir(server).resolve(dateStr + ".json");
    }

    /**
     * 从指定日期的 JSON 文件加载统计数据
     */
    public static Queue<DropStatistics> loadByDate(MinecraftServer server, String dateStr) {
        Queue<DropStatistics> result = new ConcurrentLinkedQueue<>();
        if (server == null) return result;

        try {
            if (!ServerConfig.DUSTBIN_PERSISTENT.get()) return result;
            if (ServerConfig.DROP_STATS_FILE_LIMIT.get() < 0) return result;
        } catch (Throwable ignored) {
        }

        Path file = getStatsFile(server, dateStr);
        if (!Files.exists(file)) return result;

        try {
            String content = Files.readString(file);
            JsonObject root = JsonUtils.GSON.fromJson(content, JsonObject.class);
            if (root == null || !root.has("entries")) return result;

            JsonArray entries = root.getAsJsonArray("entries");
            for (JsonElement el : entries) {
                if (el.isJsonObject()) {
                    result.add(DropStatistics.fromJson(el.getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load drop statistics from {}: {}", file, e.getMessage());
        }
        return result;
    }

    /**
     * 将统计数据保存至指定日期的 JSON 文件
     */
    public static void saveByDate(MinecraftServer server, String dateStr, Queue<DropStatistics> dropCount) {
        if (server == null) return;

        try {
            if (!ServerConfig.DUSTBIN_PERSISTENT.get()) return;
            if (ServerConfig.DROP_STATS_FILE_LIMIT.get() < 0) return;
        } catch (Throwable ignored) {
        }

        if (dropCount == null || dropCount.isEmpty()) return;

        Path dir = getStatsDir(server);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create drop statistics directory {}: {}", dir, e.getMessage());
            return;
        }

        Path file = dir.resolve(dateStr + ".json");
        JsonObject root = new JsonObject();
        root.addProperty("date", dateStr);
        JsonArray entries = new JsonArray();
        dropCount.forEach(stat -> entries.add(stat.toJson()));
        root.add("entries", entries);

        try {
            Files.writeString(file, JsonUtils.PRETTY_GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.warn("Failed to save drop statistics to {}: {}", file, e.getMessage());
        }

        // 超出文件数量上限时删除最旧的文件
        pruneOldFiles(server);
    }

    /**
     * 根据配置删除超出数量上限的旧统计文件
     */
    private static void pruneOldFiles(MinecraftServer server) {
        int limit;
        try {
            limit = ServerConfig.DROP_STATS_FILE_LIMIT.get();
        } catch (Throwable ignored) {
            return;
        }
        if (limit < 0 || limit == 0) return;

        Path dir = getStatsDir(server);
        if (!Files.exists(dir)) return;

        try {
            List<Path> files = Files.list(dir)
                    .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName, Comparator.naturalOrder()))
                    .toList();

            int toDelete = files.size() - limit;
            if (toDelete <= 0) return;

            for (int i = 0; i < toDelete; i++) {
                Files.deleteIfExists(files.get(i));
                LOGGER.debug("Pruned old drop statistics file: {}", files.get(i).getFileName());
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to prune old drop statistics files: {}", e.getMessage());
        }
    }

}
