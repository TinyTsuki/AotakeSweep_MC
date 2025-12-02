package xin.vanilla.aotake.config;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.util.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CustomConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String FILE_NAME = "common_config.json";

    @Getter
    private static JsonObject customConfig = new JsonObject();

    @Getter
    @Setter
    private static JsonObject clientConfig = new JsonObject();

    @Getter
    @Setter
    private static boolean dirty = false;

    public static Path getConfigDirectory() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(
                        Arrays.stream(AotakeSweep.ARTIFACT_ID.split("\\."))
                                .sorted()
                                .collect(Collectors.joining("."))
                );
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
                    LOGGER.debug("Loaded custom common config.");
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
                while (lock == null) {
                    try {
                        lock = channel.tryLock();
                    } catch (Exception e) {
                        if (System.currentTimeMillis() - startTime > TimeUnit.SECONDS.toMillis(timeout)) {
                            throw new RuntimeException("Failed to acquire file lock within timeout.", e);
                        }
                        Thread.sleep(100);
                    }
                    if (!isDirty()) {
                        return;
                    }
                }
                try {
                    // 清空旧内容
                    accessFile.setLength(0);
                    accessFile.write(JsonUtils.PRETTY_GSON.toJson(customConfig).getBytes(StandardCharsets.UTF_8));
                    setDirty(false);
                    LOGGER.debug("Saved custom common config.");
                    FileLock finalLock = lock;
                    new Thread(() -> {
                        try {
                            long start = System.currentTimeMillis();
                            while (finalLock.isValid()) {
                                if (System.currentTimeMillis() - start > TimeUnit.SECONDS.toMillis(timeout)) {
                                    LOGGER.error("Failed to reload config within timeout.");
                                    return;
                                }
                                Thread.sleep(100);
                            }
                            noticeReloadConfig();
                        } catch (Exception e) {
                            LOGGER.error("Error in reload watcher thread: ", e);
                        }
                    }).start();

                } catch (Exception e) {
                    LOGGER.error("Error saving custom common config: ", e);
                } finally {
                    if (lock.isValid()) {
                        try {
                            lock.release();
                        } catch (IOException e) {
                            LOGGER.warn("Failed to release file lock: ", e);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error saving custom common config (outer): ", e);
            }
        }).start();
    }

    public static void noticeReloadConfig() {
        List<Runnable> listeners = FabricLoader.getInstance().getEntrypoints("vanilla_xin_common_config_reload", Runnable.class);

        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable t) {
                LOGGER.error("Failed to invoke reload via runnable listener {}", listener.getClass().getName(), t);
            }
        }
    }

    public static String getPlayerLanguage(String uuid) {
        return JsonUtils.getString(customConfig, String.format("player.%s.language", uuid), "client");
    }

    public static String getPlayerLanguageClient(String uuid) {
        return JsonUtils.getString(clientConfig, String.format("player.%s.language", uuid), "client");
    }

    public static void setPlayerLanguage(String uuid, String language) {
        JsonUtils.setString(customConfig, String.format("player.%s.language", uuid), language);
        setDirty(true);
    }

    public static JsonObject getVirtualPermission() {
        return JsonUtils.getJsonObject(customConfig, "server.virtual_permission", new JsonObject());
    }

    public static JsonObject getVirtualPermissionClient() {
        return JsonUtils.getJsonObject(clientConfig, "server.virtual_permission", new JsonObject());
    }

    public static void setVirtualPermission(JsonObject virtualPermission) {
        JsonUtils.setJsonObject(customConfig, "server.virtual_permission", virtualPermission);
        setDirty(true);
    }
}
