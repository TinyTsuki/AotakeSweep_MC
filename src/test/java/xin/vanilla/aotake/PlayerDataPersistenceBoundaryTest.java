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

/** 防止下游模组重新引入全量玩家数据写盘。 */
public class PlayerDataPersistenceBoundaryTest {
    private static final List<String> FORBIDDEN_SYMBOLS = Arrays.asList(
            "PlayerDataManager",
            "BaniraPlayerData.flush(",
            "saveAllForWorld("
    );

    @Test
    public void playerDataUsesBaniraLifecyclePersistence() throws IOException {
        Path root = Paths.get("src", "main", "java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> path.toString().endsWith(".java"))::iterator) {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                for (String symbol : FORBIDDEN_SYMBOLS) {
                    if (source.contains(symbol)) {
                        violations.add(root.relativize(file) + " -> " + symbol);
                    }
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Player data must use Banira lifecycle persistence:\n"
                    + String.join("\n", violations));
        }
    }
}
