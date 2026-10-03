package xin.vanilla.aotake.internal.dev;

import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Explicit opt-in files belong only to the isolated acceptance run.
 */
public final class AotakePolicySmokeProbe {
    private AotakePolicySmokeProbe() {
    }

    public static boolean enabled() {
        return !FMLEnvironment.production && Boolean.getBoolean("aotake.policySmoke");
    }

    public static Path root() {
        return Paths.get(System.getProperty("aotake.policySmoke.root"));
    }

    public static boolean restart() {
        return "restart".equals(System.getProperty("aotake.policySmoke.phase"));
    }

    public static void status(String side, String text) {
        try {
            Files.write(root().resolve(side + ".status"), (text + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static boolean has(String side, String text) {
        try {
            Path file = root().resolve(side + ".status");
            return Files.exists(file) && new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains(text);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
