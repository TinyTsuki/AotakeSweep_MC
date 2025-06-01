package xin.vanilla.aotake.config;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.util.JsonUtils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class CustomConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String FILE_NAME = "common_config.json";

    private static JsonObject customConfig = new JsonObject();

    @Getter
    @Setter
    private static boolean dirty = false;

    public static Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve("vanilla.xin");
    }

    /**
     * 加载 JSON 数据
     *
     * @param notDirty 是否仅在数据不为脏时读取
     */
    public static void loadCustomConfig(boolean notDirty) {
        File dir = getConfigDirectory().toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, FILE_NAME);
        if (file.exists()) {
            if (!notDirty || !isDirty()) {
                try {
                    customConfig = JsonUtils.PRETTY_GSON.fromJson(new String(Files.readAllBytes(Paths.get(file.getPath()))), JsonObject.class);
                    LOGGER.info("Loaded custom common config.");
                } catch (Exception e) {
                    LOGGER.error("Error loading custom common config: ", e);
                }
            }
        } else {
            // 如果文件不存在，初始化默认值
            customConfig = new JsonObject();
            customConfig.add("player", new JsonObject());
            JsonObject server = new JsonObject();
            server.add("virtual_permission", new JsonObject());
            customConfig.add("server", server);
            setDirty(true);
        }
    }

    /**
     * 保存 JSON 数据
     */
    public static void saveCustomConfig() {
        long timeout = 10;
        new Thread(() -> {
            if (!isDirty()) return;
            File dir = getConfigDirectory().toFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, FILE_NAME);
            try (RandomAccessFile accessFile = new RandomAccessFile(file, "rw");
                 FileChannel channel = accessFile.getChannel()) {
                FileLock lock = null;
                long startTime = System.currentTimeMillis();
                // 尝试获取文件锁，直到超时
                while (lock == null) {
                    try {
                        lock = channel.tryLock();
                    } catch (Exception e) {
                        if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout)) {
                            throw new RuntimeException("Failed to acquire file lock within timeout.");
                        }
                        Thread.sleep(100);
                    }
                    if (!isDirty()) return;
                }
                try {
                    accessFile.write(JsonUtils.PRETTY_GSON.toJson(customConfig).getBytes());
                    setDirty(false);
                    LOGGER.info("Saved custom common config.");
                } catch (Exception e) {
                    LOGGER.error("Error saving custom common config: ", e);
                }
            } catch (Exception e) {
                LOGGER.error("Error saving custom common config: ", e);
            }
        }).start();
    }

    public static String getPlayerLanguage(String uuid) {
        return JsonUtils.getString(customConfig, String.format("player.%s.language", uuid), "client");
    }

    public static void setPlayerLanguage(String uuid, String language) {
        JsonUtils.setString(customConfig, String.format("player.%s.language", uuid), language);
        setDirty(true);
    }

    public static JsonObject getVirtualPermission() {
        return JsonUtils.getJsonObject(customConfig, "server.virtual_permission", new JsonObject());
    }

    public static void setVirtualPermission(JsonObject virtualPermission) {
        JsonUtils.setJsonObject(customConfig, "server.virtual_permission", virtualPermission);
        setDirty(true);
    }
}
