package xin.vanilla.aotake.util;

import net.minecraft.nbt.CollectionNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.StringNBT;

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

    public static boolean has(INBT root, String path) {
        return getTagByPath(root, path) != null;
    }
}
