package xin.vanilla.aotake.command;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ConfigCommandStructureTest {

    @Test
    public void exposesCommonConfigOnlyOnce() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/xin/vanilla/aotake/command/impl/ConfigCommand.java"));

        assertEquals(1, occurrences(source, "Commands.literal(\"common\")"));
        assertEquals(0, occurrences(source, "Commands.literal(\"server\")"));
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(target, index)) >= 0; index += target.length()) {
            count++;
        }
        return count;
    }
}
