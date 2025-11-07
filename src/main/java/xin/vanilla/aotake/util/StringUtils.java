package xin.vanilla.aotake.util;


import lombok.NonNull;
import xin.vanilla.aotake.enums.EnumMCColor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class StringUtils {

    public static final String FORMAT_REGEX = "%(\\d+\\$)?([-#+ 0,(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    /**
     * 将字符串转为逻辑真假
     *
     * @param s 0|1|真|假|是|否|true|false|y|n|t|f
     */
    public static boolean stringToBoolean(String s) {
        if (null == s) return false;
        switch (s.toLowerCase(Locale.ROOT).trim()) {
            case "1":
            case "真":
            case "是":
            case "true":
            case "y":
            case "t":
                return true;
            case "0":
            case "假":
            case "否":
            case "false":
            case "n":
            case "f":
            default:
                return false;
        }
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

    public static boolean isNotNull(Object s) {
        return s != null;
    }

    /**
     * @param s 字符串
     * @return 空字符串or本身
     */
    public static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static String substring(String s, int start, int end) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (end < start) {
            return s;
        }
        if (length >= start && length >= end) {
            return s.substring(start, end);
        }
        return s;
    }

    public static String substring(String s, int start) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (start > length) {
            return s;
        }
        return s.substring(start);
    }

    public static String substringEnd(String s, int len) {
        if (isNullOrEmpty(s)) {
            return "";
        }
        int length = s.length();
        if (len > length) {
            return s;
        }
        return s.substring(0, length - len);
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

    public static int getLineCount(String s) {
        if (StringUtils.isNullOrEmpty(s)) return 0;
        return StringUtils.replaceLine(s).split("\n").length;
    }

    private static final String[] NUM = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] UNIT = {"", "拾", "佰", "仟"
            , "万", "拾万", "佰万", "仟万"
            , "亿", "拾亿", "佰亿", "仟亿"
            , "兆", "拾兆", "佰兆", "仟兆"
            , "京", "拾京", "佰京", "仟京"
            , "垓", "拾垓", "佰垓", "仟垓"
            , "秭", "拾秭", "佰秭", "仟秭"
            , "穰", "拾穰", "佰穰", "仟穰"
            , "沟", "拾沟", "佰沟", "仟沟"
            , "涧", "拾涧", "佰涧", "仟涧"
            , "正", "拾正", "佰正", "仟正"
            , "载", "拾载", "佰载", "仟载"};
    private static final String[] DECIMAL = {"角", "分"};

    /**
     * 将金额转换为大写
     */
    public static String toChineseCapitalized(BigDecimal amount) {
        StringBuilder sb = new StringBuilder();
        int scale = amount.scale();
        if (scale > 2) {
            amount = amount.setScale(2, RoundingMode.HALF_UP);
        }
        String str = amount.toString();
        String[] parts = str.split("\\.");
        String integerPart = parts[0];
        String decimalPart = "00";
        if (parts.length > 1) {
            decimalPart = parts[1];
        }
        int integerLen = integerPart.length();
        int decimalLen = decimalPart.length();
        if (integerLen == 1 && integerPart.charAt(0) == '0') {
            sb.append(NUM[0]);
        } else {
            for (int i = 0; i < integerLen; i++) {
                int digit = integerPart.charAt(i) - '0';
                int unitIndex = integerLen - i - 1;
                int unit = unitIndex % 4;
                if (digit == 0) {
                    if (unit != 0 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '零') {
                        sb.append(NUM[0]);
                    }
                } else {
                    sb.append(NUM[digit]);
                    sb.append(UNIT[unit]);
                }
                if (unit == 0 && unitIndex > 0 && sb.charAt(sb.length() - 1) != '亿') {
                    sb.append(UNIT[unitIndex]);
                }
            }
        }

        sb.append("元");
        if (decimalLen == 1) {
            decimalPart += "0";
        }

        // 若小数部分不为0
        if (!decimalPart.equals("00")) {
            for (int i = 0; i < decimalLen; i++) {
                int digit = decimalPart.charAt(i) - '0';
                // 若小数位不为0
                if (digit != 0) {
                    sb.append(NUM[digit]);
                    sb.append(DECIMAL[i]);
                }
            }
        }

        if (decimalPart.equals("00")) {
            sb.append("整");
        }
        return sb.toString();
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

    public static float toFloat(String s) {
        return toFloat(s, 0);
    }

    public static float toFloat(String s, float defaultValue) {
        float result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = Float.parseFloat(s.trim());
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

    public static BigDecimal toBigDecimal(String s) {
        return toBigDecimal(s, BigDecimal.ZERO);
    }

    public static BigDecimal toBigDecimal(String s, BigDecimal defaultValue) {
        BigDecimal result = defaultValue;
        if (StringUtils.isNotNullOrEmpty(s)) {
            try {
                result = new BigDecimal(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    /**
     * 整数转罗马数字
     */
    public static String intToRoman(int num) {
        StringBuilder roman = new StringBuilder();
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        for (int i = 0; i < values.length; i++) {
            while (num >= values[i]) {
                roman.append(symbols[i]);
                num -= values[i];
            }
        }
        return roman.toString();
    }

    /**
     * 转百分数
     */
    public static String toPercent(double num) {
        return toPercent(num, 2);
    }

    /**
     * 转百分数
     */
    public static String toPercent(double num, int scale) {
        return String.format(String.format("%%.%df%%%%", scale), num * 100);
    }

    /**
     * 转百分数
     */
    public static String toPercent(BigDecimal num) {
        return toPercent(num.doubleValue());
    }

    /**
     * 转百分数
     */
    public static String toPercent(BigDecimal num, int scale) {
        return toPercent(num.doubleValue(), scale);
    }

    public static String toFixed(double d, int scale) {
        return new BigDecimal(d).setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    public static String toFixedEx(double d, int scale) {
        return toFixed(d, scale).replaceAll("0+$", "").replaceAll("[.]$", "");
    }

    public static String toFixedEx(BigDecimal d, int scale) {
        return d.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replaceAll("0+$", "").replaceAll("[.]$", "");
    }

    /**
     * 获取指定数量的某个字符串
     */
    public static String getString(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * 自定义格式化方法，支持位置重排
     *
     * @param string 格式化字符串
     * @param args   参数
     * @return 格式化后的字符串
     */
    public static String format(String string, Object... args) {
        StringBuilder result = new StringBuilder();
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

    /**
     * 将字符串转换为驼峰命名
     */
    public static String toPascalCase(String input) {
        if (isNullOrEmptyEx(input)) return "";

        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            } else {
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    public static String padOptimizedLeft(Object value, int length, String padChar) {
        return padOptimized(value, length, padChar, true);
    }

    public static String padOptimizedRight(Object value, int length, String padChar) {
        return padOptimized(value, length, padChar, false);
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

    /**
     * 判断给定字符串是否仅包含\w字符
     */
    public static boolean isWordString(String str) {
        if (StringUtils.isNullOrEmptyEx(str)) return false;
        return str.matches("^\\w+$");
    }

    /**
     * 若input不为 仅包含\w字符 的字符串
     * 则将input格式化为 'input'，并将input中的'转义为\'
     */
    public static String formatString(String input) {
        if (isWordString(input)) return input;
        return "'" + input.replaceAll("'", "\\\\'") + "'";
    }

    /**
     * 计算字符串的 MD5 值
     *
     * @param input 输入字符串
     * @return 32位小写 MD5 值
     */
    public static String md5(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * 计算文件的 MD5 值
     *
     * @param file 目标文件
     * @return 32位小写 MD5 值
     */
    public static String md5(File file) {
        if (file == null || !file.isFile()) return null;

        try (FileInputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int length;

            while ((length = in.read(buffer)) != -1) {
                md.update(buffer, 0, length);
            }
            return bytesToHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate file MD5", e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 小写十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


    public static void main(String[] args) {
        // 测试案例：不同类型的参数与格式字符串
        System.out.println(format("%2$s-%1$s-%1$s", "a", "b"));  // 输出 b-a-a
        System.out.println(format("%1$s-%2$s", "hello", "world"));  // 输出 hello-world
        System.out.println(format("%2$s-%1$s-%1$s-%2$s", "apple", "banana"));  // 输出 banana-apple-apple-banana
        System.out.println(format("%s-%d-%f", "Test", 5, 3.1415));  // 输出 Test-5-3.14
        System.out.println(format("%s-%s-%s-%s", "a", "b", "c"));  // 输出 a-b-c-%s
        System.out.println(format("%1$s-%2$s-%3$s-%4$s", "x", "y", "z"));  // 输出 x-y-z-%4$s
    }
}
