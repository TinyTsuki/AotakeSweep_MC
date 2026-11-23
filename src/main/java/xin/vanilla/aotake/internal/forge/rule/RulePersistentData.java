package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.nbt.CollectionNBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NumberNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.nbt.INBT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class RulePersistentData {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_VALUES = 4096;

    private RulePersistentData() {
    }

    static INBT find(INBT value, boolean indexed, String... path) {
        if (path.length == 0) throw new IllegalArgumentException("Empty persistent data path");
        for (String segment : path) {
            if (value instanceof CompoundNBT) {
                value = ((CompoundNBT) value).get(segment);
            } else if (indexed && value instanceof CollectionNBT) {
                CollectionNBT<?> list = (CollectionNBT<?>) value;
                int i = index(segment);
                if (i < 0 || i >= list.size()) return null;
                value = list.get(i);
            } else {
                return null;
            }
        }
        return value;
    }

    static <T> T read(INBT tag, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value;
        if (type == Boolean.class && tag instanceof NumberNBT) {
            Number number = number(tag);
            if (!(number instanceof Byte)) return null;
            int flag = number.intValue();
            value = flag == 0 ? Boolean.FALSE : flag == 1 ? Boolean.TRUE : null;
        } else if ((tag instanceof CompoundNBT && type.isAssignableFrom(Map.class))
                || (tag instanceof CollectionNBT && type.isAssignableFrom(List.class))) {
            value = new Snapshot().copy(tag, 0);
        } else {
            value = scalar(tag);
        }
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static Number number(INBT tag) {
        return ((NumberNBT) tag).getAsNumber();
    }

    private static Object scalar(INBT tag) {
        if (tag instanceof NumberNBT) return number(tag);
        if (tag instanceof StringNBT) return tag.getAsString();
        return null;
    }

    private static int index(String segment) {
        if (segment.isEmpty()) return -1;
        int index = 0;
        for (int i = 0; i < segment.length(); i++) {
            int digit = segment.charAt(i) - '0';
            if (digit < 0 || digit > 9 || index > (Integer.MAX_VALUE - digit) / 10) return -1;
            index = index * 10 + digit;
        }
        return index;
    }

    private static final class Snapshot {
        private int remaining = MAX_VALUES;

        private Object copy(INBT tag, int depth) {
            if (depth > MAX_DEPTH || remaining-- <= 0) throw tooLarge();
            if (tag instanceof CompoundNBT) {
                CompoundNBT compound = (CompoundNBT) tag;
                Set<String> keys = compound.getAllKeys();
                if (keys.size() > remaining) throw tooLarge();
                Map<String, Object> result = new LinkedHashMap<>();
                for (String key : keys) result.put(key, copy(compound.get(key), depth + 1));
                return Collections.unmodifiableMap(result);
            }
            if (tag instanceof CollectionNBT) {
                CollectionNBT<?> list = (CollectionNBT<?>) tag;
                int size = list.size();
                if (size > remaining) throw tooLarge();
                List<Object> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) result.add(copy(list.get(i), depth + 1));
                return Collections.unmodifiableList(result);
            }
            return scalar(tag);
        }

        private static IllegalArgumentException tooLarge() {
            return new IllegalArgumentException("Persistent data snapshot exceeds limits; read a narrower path");
        }
    }
}
