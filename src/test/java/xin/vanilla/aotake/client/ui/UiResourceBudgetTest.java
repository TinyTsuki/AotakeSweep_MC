package xin.vanilla.aotake.client.ui;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
public class UiResourceBudgetTest {
    @Test public void ownedResourcesStayWithinBudgetAndPlayerPageUsesSharedStyles() throws Exception {
        Map<String, byte[]> files = UiBoundaryChecks.ownResources("aotake_sweep");
        assertFalse(files.isEmpty()); assertEquals(List.of(), UiBoundaryChecks.resources(files, false));
        assertTrue(new String(files.get("player.html"), StandardCharsets.UTF_8).contains("../banira_codex/shared.css"));
    }
    @Test public void oversizedScriptsFontsBitmapsAndRemoteResourcesAreRejected() {
        for (var fixture : List.of(Map.of("page.html", "<script src='https://example.invalid/a.js'></script>".getBytes(StandardCharsets.UTF_8)),
                Map.of("large.html", new byte[12289]), Map.of("mod.css", new byte[4097]), Map.of("font.woff", new byte[1]),
                Map.of("large.png", new byte[65537]), Map.of("large.js", new byte[8193])))
            assertFalse(UiBoundaryChecks.resources(fixture, false).isEmpty());
    }
    @Test public void copiedSharedStylesAndUnsafeReferencesAreRejected() {
        assertFalse(UiBoundaryChecks.resources(Map.of("shared.css", new byte[1]), false).isEmpty());
        assertFalse(UiBoundaryChecks.resources(Map.of("page.html", "<link href='../foreign/file.css'>".getBytes(StandardCharsets.UTF_8)), false).isEmpty());
    }
}
