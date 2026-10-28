package xin.vanilla.aotake.client.ui;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

public class DustbinViewportTest {
    @Test public void manualContainerHasAViewportSizedClipBeforeAnyInput() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/apricityui/apricity/aotake_sweep/aotake.css")) {
            assertNotNull(input);
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue("Retained icons must have a non-collapsing body clip",
                    Pattern.compile("(?:html,body|body)\\s*\\{[^}]*height:100vh(?:;|})").matcher(css).find());
        }
    }
}
