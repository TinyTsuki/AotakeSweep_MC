package xin.vanilla.aotake.client.minimap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.aotake.config.ClientConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class AotakeMinimapCountdownConfigTest {
    private Object category() throws Exception {
        try {
            Field field = ClientConfig.class.getDeclaredField("minimapCountdown");
            field.setAccessible(true);
            return field.get(new ClientConfig());
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Missing independent minimap countdown config", e);
        }
    }

    private Object value(Object category, String name) throws Exception {
        Field field = category.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(category);
    }

    @Test
    public void minimapLineDefaultsEnabledAndAuto() throws Exception {
        Object category = category();
        assertEquals(true, value(category, "enabled"));
        assertEquals("AUTO", value(category, "host").toString());
    }

    @Test
    public void hostProjectionExcludesStandaloneAndRetainsExistingNativeValues() throws Exception {
        Enum<?> host = (Enum<?>) value(category(), "host");
        Object[] constants = host.getClass().getEnumConstants();
        List<String> names = Arrays.stream(constants).map(Object::toString).collect(Collectors.toList());
        assertEquals(Arrays.asList("AUTO", "XAERO", "JOURNEYMAP", "FTB_CHUNKS", "VOXELMAP", "MAP_ATLASES"), names);
        for (Object constant : constants) {
            assertEquals(constant.toString(), constant.getClass().getMethod("notificationHost").invoke(constant).toString());
        }
    }

    @Test
    public void originalProgressBarDisplayModesRemainUnchanged() throws Exception {
        category();
        ClientConfig config = new ClientConfig();
        Field field = ClientConfig.class.getDeclaredField("progressBar");
        field.setAccessible(true);
        Object progress = field.get(config);
        assertEquals("[LEAF]", value(progress, "progressBarDisplayNormal").toString());
        assertEquals("[LEAF, POLE, TEXT]", value(progress, "progressBarDisplayHold").toString());
        assertEquals(false, value(progress, "progressBarKeyApplyMode"));
    }

    private JsonObject lang(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/assets/aotake_sweep/lang/" + name + ".json")) {
            assertNotNull(input);
            return new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonObject.class);
        }
    }

    @Test
    public void compactCountdownIsFormattedInClientLanguage() throws Exception {
        JsonObject en = lang("en_us"), zh = lang("zh_cn");
        assertNotNull(en.get("format.aotake_sweep.minimap_cleanup_countdown"));
        assertNotNull(zh.get("format.aotake_sweep.minimap_cleanup_countdown"));
        assertEquals("Cleanup: 01:00", String.format(en.get("format.aotake_sweep.minimap_cleanup_countdown").getAsString(), "01:00"));
        assertEquals("\u6e05\u7406\u5012\u8ba1\u65f6\uff1a01:00",
                String.format(zh.get("format.aotake_sweep.minimap_cleanup_countdown").getAsString(), "01:00"));
    }
}
