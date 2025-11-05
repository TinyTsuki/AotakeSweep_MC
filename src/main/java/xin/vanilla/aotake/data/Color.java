package xin.vanilla.aotake.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.util.StringUtils;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class Color {
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 颜色
     */
    private int rgb = 0xFFFFFF;
    /**
     * 透明度
     */
    private int alpha = 0xFF;

    private Color() {
    }


    // region fluent

    @Nullable
    public Integer color() {
        return getColor();
    }

    public int rgb() {
        return this.rgb;
    }

    public int alpha() {
        return this.alpha;
    }

    public int argb() {
        return this.getArgb();
    }

    public int rgba() {
        return this.getRgba();
    }

    //  endregion fluent


    //  region getter

    /**
     * 获取颜色
     */
    @Nullable
    public Integer getColor() {
        return this.isEmpty() ? null : this.rgb();
    }

    /**
     * 获取RGB
     */
    public int getRgb() {
        return this.rgb();
    }

    /**
     * 获取透明度
     */
    public int getAlpha() {
        return this.alpha();
    }

    /**
     * 获取ARGB格式颜色
     */
    public int getArgb() {
        int a = this.alpha & 0xFF;
        int r = (this.rgb >> 16) & 0xFF;
        int g = (this.rgb >> 8) & 0xFF;
        int b = this.rgb & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 获取RGBA格式颜色
     */
    public int getRgba() {
        int a = this.alpha & 0xFF;
        int r = (this.rgb >> 16) & 0xFF;
        int g = (this.rgb >> 8) & 0xFF;
        int b = this.rgb & 0xFF;
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    /**
     * 颜色是否为空
     */
    public boolean isEmpty() {
        return this.alpha == 0x00;
    }

    //  endregion getter


    //  region setter

    /**
     * 设置颜色
     */
    public Color setColor(Integer rgb) {
        if (rgb == null) {
            this.alpha = 0x00;
        } else {
            this.rgb = rgb;
        }
        return this;
    }

    /**
     * 设置RGB
     */
    public Color setRgb(int rgb) {
        this.rgb = rgb;
        return this;
    }

    /**
     * 设置透明度
     */
    public Color setAlpha(int alpha) {
        this.alpha = alpha;
        return this;
    }

    /**
     * 设置ARGB颜色
     */
    public Color setArgb(Integer argb) {
        if (argb == null) {
            this.alpha = 0x00;
        } else {
            this.alpha = (argb >> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            this.rgb = (r << 16) | (g << 8) | b;
        }
        return this;
    }

    /**
     * 设置RGBA颜色
     */
    public Color setRgba(Integer rgba) {
        if (rgba == null) {
            this.alpha = 0x00;
        } else {
            this.alpha = rgba & 0xFF;
            int r = (rgba >> 24) & 0xFF;
            int g = (rgba >> 16) & 0xFF;
            int b = (rgba >> 8) & 0xFF;
            this.rgb = (r << 16) | (g << 8) | b;
        }
        return this;
    }

    //  endregion setter


    //  region static

    public static Color white() {
        return new Color();
    }

    public static Color black() {
        return new Color().setColor(0x000000);
    }

    public static Color empty() {
        return new Color().setAlpha(0x00);
    }


    public static Color fromRgb(Integer rgb) {
        return rgb(rgb);
    }

    public static Color fromRgb(Integer r, Integer g, Integer b) {
        return rgb(r, g, b);
    }

    public static Color rgb(Integer rgb) {
        return new Color().setColor(rgb);
    }

    public static Color rgb(Integer r, Integer g, Integer b) {
        if (r == null || g == null || b == null) {
            return empty();
        }
        return rgb((r << 16) | (g << 8) | b);
    }


    public static Color fromArgb(Integer argb) {
        return argb(argb);
    }

    public static Color fromARgb(Integer a, Integer r, Integer g, Integer b) {
        return argb(a, r, g, b);
    }

    public static Color argb(Integer argb) {
        return new Color().setArgb(argb);
    }

    public static Color argb(Integer a, Integer r, Integer g, Integer b) {
        if (a == null || r == null || g == null || b == null) {
            return empty();
        }
        return rgb((r << 16) | (g << 8) | b).setAlpha(a);
    }


    public static Color fromRgba(Integer rgba) {
        return rgba(rgba);
    }

    public static Color fromRgba(Integer r, Integer g, Integer b, Integer a) {
        return argb(a, r, g, b);
    }

    public static Color rgba(Integer rgba) {
        return new Color().setRgba(rgba);
    }

    public static Color rgba(Integer r, Integer g, Integer b, Integer a) {
        return argb(a, r, g, b);
    }


    /**
     * 解析颜色格式字符串
     * <p>
     * 支持的格式：
     * <p>
     * 十六进制：0xAARRGGBB、0xRRGGBB、#RRGGBB、#AARRGGBB
     * <p>
     * 十进制：R,G,B、A,R,G,B
     */
    public static Color parse(String colorStr) {
        return parse(colorStr, empty());
    }

    /**
     * 解析颜色格式字符串
     * <p>
     * 支持的格式：
     * <p>
     * 十六进制：0xAARRGGBB、0xRRGGBB、#RRGGBB、#AARRGGBB
     * <p>
     * 十进制：R,G,B、A,R,G,B
     */
    public static Color parse(String colorStr, Color defaultColor) {
        if (StringUtils.isNullOrEmptyEx(colorStr)) {
            LOGGER.debug("Color parse error: colorStr is null or empty");
            return defaultColor;
        }
        Color color = defaultColor;

        colorStr = colorStr.trim().toUpperCase();

        try {
            if (colorStr.startsWith("0X") || colorStr.startsWith("#")) {
                color = parseHexColor(colorStr);
            } else if (colorStr.contains(",")) {
                color = parseDecimalColor(colorStr);
            } else {
                LOGGER.debug("Color parse error: unsupported color format: {}", colorStr);
            }
        } catch (Exception e) {
            LOGGER.debug("Color parse error: failed to parse color string: {} - {}", colorStr, e.getMessage());
        }
        return color;
    }

    /**
     * 解析十六进制颜色格式
     */
    private static Color parseHexColor(String hexStr) {
        // 移除前缀
        String cleanHex = hexStr.replace("0X", "").replace("#", "");

        int alpha = 0xFF;
        int red, green, blue;

        // RGB
        if (cleanHex.length() == 6) {
            red = Integer.parseInt(cleanHex.substring(0, 2), 16);
            green = Integer.parseInt(cleanHex.substring(2, 4), 16);
            blue = Integer.parseInt(cleanHex.substring(4, 6), 16);
        }
        // ARGB
        else if (cleanHex.length() == 8) {
            alpha = Integer.parseInt(cleanHex.substring(0, 2), 16);
            red = Integer.parseInt(cleanHex.substring(2, 4), 16);
            green = Integer.parseInt(cleanHex.substring(4, 6), 16);
            blue = Integer.parseInt(cleanHex.substring(6, 8), 16);
        } else {
            throw new IllegalArgumentException("Hex color length must be 6 or 8");
        }
        return argb(alpha, red, green, blue);
    }

    /**
     * 解析十进制颜色格式
     */
    private static Color parseDecimalColor(String decimalStr) {
        String[] parts = decimalStr.split(",");

        int red, green, blue;
        int alpha = 0xFF;

        // RGB
        if (parts.length == 3) {
            red = Integer.parseInt(parts[0].trim());
            green = Integer.parseInt(parts[1].trim());
            blue = Integer.parseInt(parts[2].trim());
        }
        // ARGB
        else if (parts.length == 4) {
            alpha = Integer.parseInt(parts[0].trim());
            red = Integer.parseInt(parts[1].trim());
            green = Integer.parseInt(parts[2].trim());
            blue = Integer.parseInt(parts[3].trim());
        } else {
            throw new IllegalArgumentException("Decimal color length must be 3 or 4");
        }

        // 验证数值范围
        validateColorComponent(red, "red");
        validateColorComponent(green, "green");
        validateColorComponent(blue, "blue");
        validateColorComponent(alpha, "alpha");

        return argb(alpha, red, green, blue);
    }

    /**
     * 验证颜色分量范围 (0-255)
     */
    private static void validateColorComponent(int value, String name) {
        if (value < 0x00 || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be in range 0x00 - 0xFF");
        }
    }

    //  endregion static


    @Override
    public String toString() {
        return "Color{" +
                "rgb=" + rgb +
                ", alpha=" + alpha +
                '}';
    }

}
