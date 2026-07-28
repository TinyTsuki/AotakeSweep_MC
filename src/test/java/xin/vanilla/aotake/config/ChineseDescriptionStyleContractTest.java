package xin.vanilla.aotake.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 锁定中文运行时说明的标点与 tooltip 行宽，避免跨分支迁移后再次漂移。
 */
public class ChineseDescriptionStyleContractTest {
    private static final Pattern ZH_TOOLTIP =
            Pattern.compile("zh_cn\\s*=\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");

    @Test
    public void chineseDescriptionsUseCompactTooltipStyle() throws Exception {
        assertChineseResourcesHaveNoFullStop();
        assertConfigTooltipsAreCompact();
    }

    private void assertChineseResourcesHaveNoFullStop() throws Exception {
        try (Stream<Path> files = Files.walk(Paths.get("src/main/resources"))) {
            for (Path path : (Iterable<Path>) files
                    .filter(p -> p.getFileName().toString().equals("zh_cn.json"))::iterator) {
                assertFalse(path + " contains a trailing Chinese full stop", read(path).contains("。"));
            }
        }
    }

    private void assertConfigTooltipsAreCompact() throws Exception {
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher matcher = ZH_TOOLTIP.matcher(read(path));
                while (matcher.find()) {
                    String tooltip = matcher.group(1);
                    assertFalse(path + " contains a Chinese full stop", tooltip.contains("。"));
                    for (String line : tooltip.split("\\\\n", -1)) {
                        assertTrue(path + " has an overlong zh_cn tooltip line: " + line,
                                line.length() <= 48);
                    }
                }
            }
        }
    }

    private String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
