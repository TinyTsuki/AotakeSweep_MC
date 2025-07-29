package xin.vanilla.aotake.util;

import net.minecraft.nbt.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NBTPathUtils {

    private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)(\\[(\\d+)])?");

    public static Tag getTagByPath(Tag root, String path) {
        String[] parts = path.split("\\.");
        Tag current = root;

        for (String part : parts) {
            if (current == null) return null;
            Matcher matcher = PATH_PATTERN.matcher(part);
            if (!matcher.matches()) return null;

            String key = matcher.group(1);
            String indexStr = matcher.group(3);

            if (current instanceof CompoundTag compound) {
                if (!compound.contains(key)) return null;
                current = compound.get(key);
            } else {
                return null;
            }

            if (indexStr != null && current instanceof CollectionTag<?> list) {
                int index = Integer.parseInt(indexStr);
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
            }
        }

        return current;
    }

    public static String getString(Tag root, String path, String defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof StringTag) ? tag.getAsString() : defaultVal;
    }

    public static boolean getBoolean(Tag root, String path, boolean defaultVal) {
        Tag tag = getTagByPath(root, path);
        if (tag instanceof ByteTag byteTag) {
            return byteTag.getAsByte() != 0;
        } else if (tag instanceof IntTag intTag) {
            return intTag.getAsInt() != 0;
        }
        return defaultVal;
    }

    public static int getByte(Tag root, String path, int defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsByte() : defaultVal;
    }

    public static int getShort(Tag root, String path, int defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsShort() : defaultVal;
    }

    public static int getInt(Tag root, String path, int defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsInt() : defaultVal;
    }

    public static float getFloat(Tag root, String path, float defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsFloat() : defaultVal;
    }

    public static long getLong(Tag root, String path, long defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsLong() : defaultVal;
    }

    public static double getDouble(Tag root, String path, double defaultVal) {
        Tag tag = getTagByPath(root, path);
        return (tag instanceof NumericTag numericTag) ? numericTag.getAsDouble() : defaultVal;
    }

    public static boolean has(Tag root, String path) {
        return getTagByPath(root, path) != null;
    }
}
