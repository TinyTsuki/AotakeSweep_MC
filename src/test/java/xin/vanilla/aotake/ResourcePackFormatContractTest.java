package xin.vanilla.aotake;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class ResourcePackFormatContractTest {
    @Test
    public void minecraft1182UsesResourcePackFormatEight() throws Exception {
        String metadata = new String(Files.readAllBytes(
                Paths.get("src/main/resources/pack.mcmeta")), StandardCharsets.UTF_8);

        assertTrue(metadata.contains("\"pack_format\": 8"));
    }
}
