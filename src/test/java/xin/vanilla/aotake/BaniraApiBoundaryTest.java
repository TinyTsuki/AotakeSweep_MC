package xin.vanilla.aotake;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

/** 防止服务端运行时和玩家数据访问重新依赖 Banira 的旧工具入口。 */
public class BaniraApiBoundaryTest {
    @Test
    public void runtimeAccessUsesStableBaniraFacades() throws IOException {
        Path root = Paths.get("src", "main", "java");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (source.contains("xin.vanilla.banira.common.util.BaniraServerUtils")) {
                    violations.add(root.relativize(file).toString());
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Runtime access must use BaniraServer/BaniraPlayerData: " + String.join(", ", violations));
        }
    }
}
