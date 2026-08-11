package xin.vanilla.aotake;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

/** 防止生产代码重新依赖 Banira 的旧运行时入口或 internal 实现。 */
public class BaniraApiBoundaryTest {
    private static final List<String> FORBIDDEN_SYMBOLS = Arrays.asList(
            "BaniraServerUtils",
            "xin.vanilla.banira.internal.",
            "playerDataManager",
            "PlayerDataManager",
            "BaniraPlayerData.flush(",
            "saveAllForWorld(",
            "IVirtualPermissionType"
    );

    @Test
    public void productionCodeUsesStableBaniraApi() throws IOException {
        Path root = Paths.get("src", "main", "java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (String symbol : FORBIDDEN_SYMBOLS) {
                    if (source.contains(symbol)) {
                        violations.add(root.relativize(file) + " -> " + symbol);
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Production code must use stable Banira APIs:\n" + String.join("\n", violations));
        }
    }
}
