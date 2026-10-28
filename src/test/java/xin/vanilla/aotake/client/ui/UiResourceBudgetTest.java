package xin.vanilla.aotake.client.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class UiResourceBudgetTest {
    @Test
    public void ownedResourcesStayWithinBudgetAndPlayerPageUsesSharedStyles() throws Exception {
        Map<String, byte[]> files = UiBoundaryChecks.ownResources("aotake_sweep");
        assertFalse(files.isEmpty());
        assertEquals(List.of(), UiBoundaryChecks.resources(files, false));
        assertFalse("Player configuration reuses Banira's shared template", files.containsKey("player.html"));
    }

    @Test
    public void oversizedScriptsFontsBitmapsAndRemoteResourcesAreRejected() {
        for (var fixture : List.of(Map.of("page.html", "<script src='https://example.invalid/a.js'></script>".getBytes(StandardCharsets.UTF_8)),
                Map.of("large.html", new byte[12289]), Map.of("mod.css", new byte[4097]), Map.of("font.woff", new byte[1]),
                Map.of("large.png", new byte[65537]), Map.of("large.js", new byte[8193])))
            assertFalse(UiBoundaryChecks.resources(fixture, false).isEmpty());
    }

    @Test
    public void copiedSharedStylesAndUnsafeReferencesAreRejected() {
        assertFalse(UiBoundaryChecks.resources(Map.of("shared.css", new byte[1]), false).isEmpty());
        assertFalse(UiBoundaryChecks.resources(Map.of("page.html", "<link href='../foreign/file.css'>".getBytes(StandardCharsets.UTF_8)), false).isEmpty());
    }
}
