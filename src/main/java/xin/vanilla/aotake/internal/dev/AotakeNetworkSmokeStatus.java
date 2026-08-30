package xin.vanilla.aotake.internal.dev;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** 开发专服烟测的 UTF-8 状态记录器。 */
public final class AotakeNetworkSmokeStatus {
    public static final String ENABLE_PROPERTY = "aotake.networkSmoke";
    private static final Logger LOGGER = LogManager.getLogger();

    private AotakeNetworkSmokeStatus() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static String phase() {
        return System.getProperty("aotake.networkSmoke.phase", "").trim();
    }

    public static synchronized void append(String line) {
        String configured = System.getProperty("aotake.networkSmoke.status", "").trim();
        if (configured.isEmpty()) throw new IllegalStateException("Missing aotake.networkSmoke.status");
        Path path = Paths.get(configured);
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            LOGGER.info("Aotake network smoke: {}", line);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to write network smoke status " + path, error);
        }
    }
}
