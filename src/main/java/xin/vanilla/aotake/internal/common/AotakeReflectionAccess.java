package xin.vanilla.aotake.internal.common;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/** Aotake 过滤表达式使用的普通反射入口，不尝试绕过 final 或 JVM 模块限制。 */
public final class AotakeReflectionAccess {
    private AotakeReflectionAccess() {
    }

    @Nullable
    public static Class<?> classByName(String className) {
        if (className == null || className.trim().isEmpty()) return null;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    public static Class<?> classOf(Object value) {
        return value == null ? null : value.getClass();
    }

    @Nullable
    public static Object fieldValue(Class<?> declaringClass, Object instance, String fieldName) {
        return fieldValue(declaringClass, instance, fieldName, false);
    }

    @Nullable
    public static Object fieldValue(Class<?> declaringClass, Object instance, String fieldName, boolean includeParents) {
        if (declaringClass == null || fieldName == null || fieldName.isEmpty()) return null;
        Class<?> current = declaringClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                if (!includeParents) return null;
                current = current.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
