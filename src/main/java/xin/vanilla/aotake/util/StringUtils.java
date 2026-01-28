package xin.vanilla.aotake.util;


import lombok.NonNull;
import xin.vanilla.aotake.enums.EnumMCColor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class StringUtils {

    public static final String FORMAT_REGEX = "%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    /**
     * 将字符串转为逻辑真假
     *
     * @param s 0|1|真|假|是|否|true|false|y|n|t|f
     */
    public static boolean stringToBoolean(String s) {
        if (null == s) return false;
        return switch (s.toLowerCase(Locale.ROOT).trim()) {
            case "1", "真", "是", "true", "y", "t" -> true;
            default -> false;
        };
    }

    public static boolean isNullOrEmpty(String s) {
        return null == s || s.isEmpty();
    }

    public static boolean isNullOrEmptyEx(String s) {
        return null == s || s.trim().isEmpty();
    }

    public static boolean isNotNullOrEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    public static String toString(String s, String emptyDefault) {
        return StringUtils.isNullOrEmpty(s) ? emptyDefault : s;
    }

    /**
     * 替换换行符
     */
    @NonNull
    public static String replaceLine(String s) {
        if (s == null) return "";
        return s.replaceAll("<br>", "\n")
                .replaceAll("\\\\n", "\n")
                .replaceAll("\\\\r", "\r")
                .replaceAll("\\n", "\n")
                .replaceAll("\\r", "\r")
                .replaceAll("\r\n", "\n");
    }

    public static int toInt(String s) {
        return toInt(s, 0);
    }

    public static int toInt(String s, int defaultValue) {
        int result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static long toLong(String s) {
        return toLong(s, 0);
    }

    public static long toLong(String s, long defaultValue) {
        long result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static double toDouble(String s) {
        return toDouble(s, 0);
    }

    public static double toDouble(String s, double defaultValue) {
        double result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public static String toFixed(double d, int scale) {
        return new BigDecimal(d).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    public static String toFixedEx(double d, int scale) {
        return toFixed(d, scale).replaceAll("0+$", "").replaceAll("[.]$", "");
    }

    /**
     * 获取指定数量的某个字符串
     */
    public static String getString(String s, int count) {
        return String.valueOf(s).repeat(Math.max(0, count));
    }

    /**
     * 自定义格式化方法，支持位置重排
     *
     * @param string 格式化字符串
     * @param args   参数
     * @return 格式化后的字符串
     */
    public static String format(String string, Object... args) {
        // 使用正则匹配格式化占位符
        Pattern pattern = Pattern.compile(FORMAT_REGEX);
        Matcher matcher = pattern.matcher(string);
        int i = 0;
        while (matcher.find()) {
            // 获取当前占位符
            String placeholder = matcher.group();

            // 获取位置标识符，如 %1$s 中的 1
            int index = placeholder.contains("$") ? toInt(placeholder.split("\\$")[0].substring(1)) - 1 : -1;
            // 如果占位符中没有显式的数字索引，则默认按顺序处理
            if (index == -1) {
                index = i;
            }
            // 检查是否有足够的参数
            String formattedArg = placeholder;
            if (index < args.length) {
                formattedArg = formatArgument(placeholder, args[index]);
            }
            // 替换占位符为对应的参数
            string = string.replaceFirst(Pattern.quote(placeholder), formattedArg.replaceAll("\\$", "\\\\\\$"));
            i++;
        }
        return string;
    }

    /**
     * 根据占位符的类型格式化参数
     *
     * @param placeholder 占位符
     * @param arg         参数
     */
    private static String formatArgument(String placeholder, Object arg) {
        if (arg == null) return "null";  // 如果参数是 null，直接返回 null
        try {
            return String.format(placeholder.replaceAll("^%\\d+\\$", "%"), arg);  // 默认处理
        } catch (Exception e) {
            // 如果出现异常，直接转换为字符串
            return arg.toString();
        }
    }

    public static int argbToHex(String argb) {
        try {
            if (argb.startsWith("#")) {
                return (int) Long.parseLong(argb.substring(1), 16);
            } else if (argb.startsWith("0x")) {
                return (int) Long.parseLong(argb.substring(2), 16);
            } else {
                return (int) Long.parseLong(argb, 16);
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * RGB颜色转换为Minecraft颜色代码
     *
     * @param color 颜色值 (ARGB: 0xAARRGGBB 或 RGB: 0xRRGGBB)
     * @return 颜色代码
     */
    public static String argbToMinecraftColorString(int color) {
        return "§" + argbToMinecraftColor(color).getCode();
    }

    public static EnumMCColor argbToMinecraftColor(int color) {
        // 获取 RGB 分量
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        // 颜色匹配
        double closestDistance = Double.MAX_VALUE;
        // 默认为白色
        EnumMCColor result = EnumMCColor.WHITE;
        for (EnumMCColor mcColor : EnumMCColor.values()) {
            int colorRGB = mcColor.getColor();
            int r = (colorRGB >> 16) & 0xFF;
            int g = (colorRGB >> 8) & 0xFF;
            int b = colorRGB & 0xFF;
            // 加权欧几里得距离计算
            double distance = Math.sqrt(2 * Math.pow(red - r, 2) + 4 * Math.pow(green - g, 2) + 3 * Math.pow(blue - b, 2));
            if (distance < closestDistance) {
                closestDistance = distance;
                result = mcColor;
            }
        }
        return result;
    }

    public static String padOptimizedLeft(Object value, int length, String padChar) {
        return padOptimized(value, length, padChar, true);
    }

    /**
     * 在字符串前或后补全字符
     */
    public static String padOptimized(Object value, int length, String padChar, boolean left) {
        String str = String.valueOf(value);
        int currentLength = str.length();

        if (length <= currentLength) return str;

        char paddingChar = padChar != null && !padChar.isEmpty() ? padChar.charAt(0) : ' ';
        char[] chars = new char[length - currentLength];
        Arrays.fill(chars, paddingChar);

        return left ? new String(chars) + str : str + new String(chars);
    }

}
