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

/** 防止子 mod 重新依赖 Banira 的旧工具入口或内部实现。 */
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
                if (source.contains("xin.vanilla.banira.internal")) {
                    violations.add(root.relativize(file).toString() + " (internal)");
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Banira access must use stable public APIs: " + String.join(", ", violations));
        }
    }
}
