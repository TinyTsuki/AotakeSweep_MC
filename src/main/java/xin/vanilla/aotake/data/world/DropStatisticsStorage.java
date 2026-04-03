package xin.vanilla.aotake.data.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.storage.FolderName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.CommonConfig;
import xin.vanilla.aotake.data.DropStatistics;
import xin.vanilla.banira.BaniraCodex;
import xin.vanilla.banira.common.util.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 掉落物统计
 */
public class DropStatisticsStorage {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Object LEGACY_MIGRATE_LOCK = new Object();

    /**
     * 获取当日统计文件的存储路径
     */
    public static Path getStatsDir(MinecraftServer server) {
        Path newDir = BaniraCodex.BANIRA_WORLD_DATA_PATH.get().resolve(AotakeSweep.MODID).resolve("drop_stats");
        if (server != null) {
            migrateLegacyStatsDirAndDelete(server, newDir);
        }
        return newDir;
    }

    /**
     * 自原 {@code stats/} 下本 mod 子目录迁移至 Banira世界数据根下的同名子目录，并删除旧目录
     */
    private static void migrateLegacyStatsDirAndDelete(MinecraftServer server, Path newDir) {
        Path oldDir = server.getWorldPath(FolderName.PLAYER_STATS_DIR).resolve(AotakeSweep.MODID);
        synchronized (LEGACY_MIGRATE_LOCK) {
            try {
                if (!Files.exists(oldDir) || !Files.isDirectory(oldDir)) {
                    return;
                }
                Files.createDirectories(newDir);
                try (Stream<Path> list = Files.list(oldDir)) {
                    for (Path oldFile : list.collect(Collectors.toList())) {
                        if (!Files.isRegularFile(oldFile)) {
                            continue;
                        }
                        String name = oldFile.getFileName().toString();
                        if (!name.endsWith(".json")) {
                            continue;
                        }
                        Path dest = newDir.resolve(name);
                        if (!Files.exists(dest)) {
                            Files.move(oldFile, dest, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            Files.deleteIfExists(oldFile);
                        }
                    }
                }
                try (Stream<Path> walk = Files.walk(oldDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            LOGGER.warn("Failed to delete legacy stats path {}: {}", p, e.getMessage());
                        }
                    });
                }
                LOGGER.info("Migrated drop statistics from {} to {} and removed legacy directory", oldDir, newDir);
            } catch (IOException e) {
                LOGGER.warn("Failed to migrate drop statistics from {} to {}: {}", oldDir, newDir, e.getMessage());
            }
        }
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
            if (!CommonConfig.get().base().dustbin().dustbinPersistent()) return result;
            if (CommonConfig.get().base().dustbin().dropStatsFileLimit() < 0) return result;
        } catch (Throwable ignored) {
        }

        Path file = getStatsFile(server, dateStr);
        if (!Files.exists(file)) return result;

        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = JsonUtils.parseObject(content);
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
            if (!CommonConfig.get().base().dustbin().dustbinPersistent()) return;
            if (CommonConfig.get().base().dustbin().dropStatsFileLimit() < 0) return;
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
            Files.write(file, JsonUtils.PRETTY_GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
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
            limit = CommonConfig.get().base().dustbin().dropStatsFileLimit();
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
                    .collect(Collectors.toList());

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
