package xin.vanilla.aotake.internal.forge.rule;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.*;
import net.minecraft.network.datasync.DataParameter;
import xin.vanilla.aotake.rule.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Cold-path binding compilation. Missing paths are errors; optional values alone may be null. */
public final class ForgeRuleBindings {
    private ForgeRuleBindings() { }
    @FunctionalInterface public interface Reader { Object read(Object object); }

    public static Reader field(RuleBindingPlan.Binding binding, Class<?> root, ClassLoader loader) {
        String path = binding.payload(); Class<?> declared = root; int prefix = -1;
        for (int i = path.length() - 1; i > 0; i--) {
            if (path.charAt(i) != ':' && path.charAt(i) != '.') continue;
            try { declared = Class.forName(path.substring(0, i), false, loader); prefix = i; break; }
            catch (ClassNotFoundException ignored) { }
            catch (LinkageError error) { throw diagnostic(binding, "Cannot load declaring class: " + error); }
        }
        List<String> segments = split(path.substring(prefix + 1), true);
        List<Reader> readers = new ArrayList<>(); Type current = declared;
        Class<?> receiverType = declared;
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i); Class<?> raw = raw(current);
            if (raw == Optional.class) {
                current = argument(current, 0, binding); readers.add(value -> ((Optional<?>) value).orElse(null)); raw = raw(current);
            }
            if (raw.isArray() || List.class.isAssignableFrom(raw)) {
                final int index;
                try { index = Integer.parseInt(segment); } catch (NumberFormatException error) { throw diagnostic(binding, "Expected an index: " + segment); }
                if (index < 0) throw diagnostic(binding, "Negative index");
                if (raw.isArray()) { current = raw.getComponentType(); readers.add(value -> index < Array.getLength(value) ? Array.get(value, index) : null); }
                else { current = argument(current, 0, binding); readers.add(value -> index < ((List<?>) value).size() ? ((List<?>) value).get(index) : null); }
            } else if (Map.class.isAssignableFrom(raw)) {
                Class<?> keyType = raw(argument(current, 0, binding)); Object key;
                try {
                    if (keyType == String.class) key = segment;
                    else if (keyType == Integer.class) key = Integer.valueOf(segment);
                    else if (keyType == Long.class) key = Long.valueOf(segment);
                    else throw diagnostic(binding, "Unsupported map key type");
                } catch (NumberFormatException error) { throw diagnostic(binding, "Invalid map key"); }
                final Object mapKey = key;
                current = argument(current, 1, binding); readers.add(value -> ((Map<?, ?>) value).get(mapKey));
            } else {
                Field field = find(raw, segment, binding);
                if (binding.kind().equals("synced") && i == 0) {
                    if (!Modifier.isStatic(field.getModifiers()) || !DataParameter.class.isAssignableFrom(field.getType()))
                        throw diagnostic(binding, "Synced field must be a static DataParameter");
                    current = argument(field.getGenericType(), 0, binding);
                    readers.add(value -> {
                        try { return ((Entity) value).getEntityData().get((DataParameter<?>) field.get(null)); }
                        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read synced field", error); }
                    });
                } else {
                    current = field.getGenericType();
                    readers.add(value -> {
                        try { return field.get(Modifier.isStatic(field.getModifiers()) ? null : value); }
                        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot read field", error); }
                    });
                }
            }
        }
        Class<?> terminal = raw(current);
        if (terminal == Optional.class) terminal = raw(argument(current, 0, binding));
        if (terminal == OptionalInt.class || terminal == AtomicInteger.class) terminal = Integer.class;
        if (terminal == OptionalLong.class || terminal == AtomicLong.class) terminal = Long.class;
        if (terminal == OptionalDouble.class) terminal = Double.class;
        if (terminal == AtomicBoolean.class) terminal = Boolean.class;
        if (!binding.type().isAssignableFrom(box(terminal))) throw diagnostic(binding, "Declared binding type disagrees with field type " + terminal.getName());
        return object -> {
            if (!receiverType.isInstance(object)) throw new IllegalStateException("Binding receiver must be " + receiverType.getName());
            Object value = object;
            for (Reader reader : readers) { if (value == null) return null; value = reader.read(value); }
            return unwrap(value);
        };
    }

    public static Reader nbt(RuleBindingPlan.Binding binding) {
        List<String> parts = split(binding.payload(), false);
        List<Reader> readers = new ArrayList<>();
        for (String part : parts) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([^\\[\\]]+)(?:\\[([0-9]+)\\])?").matcher(part);
            if (!m.matches()) throw diagnostic(binding, "Invalid NBT path segment: " + part);
            String key = m.group(1); String indexText = m.group(2);
            readers.add(value -> {
                if (!(value instanceof CompoundNBT)) throw new IllegalStateException("Expected compound at " + key);
                return ((CompoundNBT) value).get(key);
            });
            if (indexText != null) {
                final int index;
                try { index = Integer.parseInt(indexText); } catch (NumberFormatException error) { throw diagnostic(binding, "NBT index too large"); }
                readers.add(value -> {
                    if (!(value instanceof CollectionNBT)) throw new IllegalStateException("Expected NBT list at " + key);
                    CollectionNBT<?> list = (CollectionNBT<?>) value;
                    return index < list.size() ? list.get(index) : null;
                });
            }
        }
        return root -> {
            Object value = root;
            for (Reader reader : readers) { if (value == null) return null; value = reader.read(value); }
            return value == null ? null : nbtValue((INBT) value, binding.type());
        };
    }
    private static Object nbtValue(INBT tag, Class<?> type) {
        Object result;
        if (tag instanceof NumberNBT) result = ((NumberNBT) tag).getAsNumber();
        else if (tag instanceof StringNBT) result = tag.getAsString();
        else if (tag instanceof CollectionNBT && (type.isArray() || type == List.class || type == Collection.class)) {
            CollectionNBT<?> list = (CollectionNBT<?>) tag;
            Class<?> element = type.isArray() ? box(type.getComponentType()) : Object.class;
            Object array = type.isArray() ? Array.newInstance(type.getComponentType(), list.size()) : null;
            List<Object> items = array == null ? new ArrayList<>(list.size()) : null;
            for (int i = 0; i < list.size(); i++) {
                Object value = nbtValue(list.get(i), element);
                if (array != null) Array.set(array, i, value); else items.add(value);
            }
            return array != null ? array : Collections.unmodifiableList(items);
        } else throw new IllegalStateException("Unsupported NBT value for " + type.getName());
        if (!type.isInstance(result)) throw new IllegalStateException("NBT type mismatch: " + result.getClass().getName() + " != " + type.getName());
        return result;
    }
    private static List<String> split(String path, boolean fieldPath) {
        List<String> result = new ArrayList<>(); StringBuilder part = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length()) part.append(path.charAt(++i));
            else if (c == '.' || fieldPath && c == ':') { result.add(part.toString()); part.setLength(0); }
            else part.append(c);
        }
        result.add(part.toString());
        if (result.stream().anyMatch(String::isEmpty)) throw new RuleDiagnostic(0, "Empty path segment");
        return result;
    }
    private static Field find(Class<?> root, String name, RuleBindingPlan.Binding binding) {
        for (Class<?> type = root; type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
            catch (RuntimeException error) { throw diagnostic(binding, "Cannot access field " + name); }
        }
        throw diagnostic(binding, "Missing field " + root.getName() + ":" + name);
    }
    private static Type argument(Type type, int index, RuleBindingPlan.Binding binding) {
        if (!(type instanceof ParameterizedType)) throw diagnostic(binding, "Missing generic type information");
        Type value = ((ParameterizedType) type).getActualTypeArguments()[index];
        if (!(value instanceof Class) && !(value instanceof ParameterizedType)) throw diagnostic(binding, "Ambiguous generic type");
        return value;
    }
    private static Class<?> raw(Type type) { return type instanceof Class ? (Class<?>) type : (Class<?>) ((ParameterizedType) type).getRawType(); }
    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class; if (type == long.class) return Long.class;
        if (type == float.class) return Float.class; if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class; if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class; if (type == char.class) return Character.class;
        return Void.class;
    }
    private static Object unwrap(Object value) {
        while (value instanceof Optional) value = ((Optional<?>) value).orElse(null);
        if (value instanceof OptionalInt) return ((OptionalInt) value).isPresent() ? ((OptionalInt) value).getAsInt() : null;
        if (value instanceof OptionalLong) return ((OptionalLong) value).isPresent() ? ((OptionalLong) value).getAsLong() : null;
        if (value instanceof OptionalDouble) return ((OptionalDouble) value).isPresent() ? ((OptionalDouble) value).getAsDouble() : null;
        if (value instanceof AtomicInteger) return ((AtomicInteger) value).get();
        if (value instanceof AtomicLong) return ((AtomicLong) value).get();
        if (value instanceof AtomicBoolean) return ((AtomicBoolean) value).get();
        return value;
    }
    private static RuleDiagnostic diagnostic(RuleBindingPlan.Binding b, String text) { return new RuleDiagnostic(b.offset(), text); }
}
