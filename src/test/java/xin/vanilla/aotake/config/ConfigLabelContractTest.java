package xin.vanilla.aotake.config;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigLabelContractTest {

    @Test
    public void collapsibleLabelsStayConcise() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/xin/vanilla/aotake/config/CommonConfig.java")), StandardCharsets.UTF_8);

        assertFalse(source.contains("文案/语音已迁移"));
        assertFalse(source.contains("原 TOML"));
        assertFalse(source.contains("formerly toml"));
        assertTrue(source.contains("zh_cn = \"实体捕获\", en_us = \"Entity capture\""));
    }
}
