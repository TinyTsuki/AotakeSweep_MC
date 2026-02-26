package xin.vanilla.aotake.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.enums.EnumDustbinScaleMode;
import xin.vanilla.aotake.util.JsonUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 垃圾箱 GUI 资源包配置
 */
public final class DustbinGuiConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ResourceLocation CONFIG_LOCATION = AotakeSweep.createIdentifier("gui/dustbin.json");

    @Getter
    private static EnumDustbinScaleMode scaleMode = EnumDustbinScaleMode.FIT;
    @Getter
    private static int xOffset = 0;
    @Getter
    private static int yOffset = 0;
    @Getter
    private static int buttonXOffset = 0;
    @Getter
    private static int buttonYOffset = 0;

    private DustbinGuiConfig() {
    }

    public static void reload() {
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(CONFIG_LOCATION).orElse(null);
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonUtils.GSON.fromJson(reader, JsonObject.class);
                if (json.has("scale_mode")) {
                    scaleMode = EnumDustbinScaleMode.fromString(json.get("scale_mode").getAsString());
                }
                if (json.has("x_offset")) {
                    xOffset = parseInt(json.get("x_offset"));
                }
                if (json.has("y_offset")) {
                    yOffset = parseInt(json.get("y_offset"));
                }
                if (json.has("button_x_offset")) {
                    buttonXOffset = parseInt(json.get("button_x_offset"));
                }
                if (json.has("button_y_offset")) {
                    buttonYOffset = parseInt(json.get("button_y_offset"));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load dustbin gui config, using default: {}", e.getMessage());
            scaleMode = EnumDustbinScaleMode.FIT;
            xOffset = 0;
            yOffset = 0;
            buttonXOffset = 0;
            buttonYOffset = 0;
        }
    }

    private static int parseInt(JsonElement element) {
        if (element == null || element.isJsonNull()) return 0;
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsInt();
            } catch (NumberFormatException e) {
                try {
                    return (int) element.getAsDouble();
                } catch (NumberFormatException e2) {
                    return Integer.parseInt(element.getAsString());
                }
            }
        }
        return 0;
    }

}
