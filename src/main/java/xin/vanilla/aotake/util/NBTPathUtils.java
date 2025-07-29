package xin.vanilla.aotake.util;

import net.minecraft.nbt.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NBTPathUtils {

    private static final Pattern PATH_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)(\\[(\\d+)])?");

    public static INBT getTagByPath(INBT root, String path) {
        String[] parts = path.split("\\.");
        INBT current = root;

        for (String part : parts) {
            if (current == null) return null;
            Matcher matcher = PATH_PATTERN.matcher(part);
            if (!matcher.matches()) return null;

            String key = matcher.group(1);
            String indexStr = matcher.group(3);

            if (current instanceof CompoundNBT) {
                CompoundNBT compound = (CompoundNBT) current;
                if (!compound.contains(key)) return null;
                current = compound.get(key);
            } else {
                return null;
            }

            if (indexStr != null && current instanceof CollectionNBT) {
                int index = Integer.parseInt(indexStr);
                CollectionNBT<?> list = (CollectionNBT<?>) current;
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
            }
        }

        return current;
    }

    public static String getString(INBT root, String path, String defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof StringNBT) ? tag.getAsString() : defaultVal;
    }

    public static boolean getBoolean(INBT root, String path, boolean defaultVal) {
        INBT tag = getTagByPath(root, path);
        if (tag instanceof ByteNBT) {
            return ((ByteNBT) tag).getAsByte() != 0;
        } else if (tag instanceof IntNBT) {
            return ((IntNBT) tag).getAsInt() != 0;
        }
        return defaultVal;
    }

    public static int getByte(INBT root, String path, int defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsByte() : defaultVal;
    }

    public static int getShort(INBT root, String path, int defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsShort() : defaultVal;
    }

    public static int getInt(INBT root, String path, int defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsInt() : defaultVal;
    }

    public static float getFloat(INBT root, String path, float defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsFloat() : defaultVal;
    }

    public static long getLong(INBT root, String path, long defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsLong() : defaultVal;
    }

    public static double getDouble(INBT root, String path, double defaultVal) {
        INBT tag = getTagByPath(root, path);
        return (tag instanceof NumberNBT) ? ((NumberNBT) tag).getAsDouble() : defaultVal;
    }

    public static boolean has(INBT root, String path) {
        return getTagByPath(root, path) != null;
    }
}
