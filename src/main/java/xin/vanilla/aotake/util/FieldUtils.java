package xin.vanilla.aotake.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

public class FieldUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    public static Class<?> getClass(Object o) {
        return o.getClass();
    }

    public static Class<?> getClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.error("Failed to get class {}", className, e);
        }
        return null;
    }

    /**
     * 获取 类中声明的私有 target 字段值
     *
     * @param clazz     类
     * @param instance  实例
     * @param fieldName 字段名称
     */
    public static Object getPrivateFieldValue(Class<?> clazz, Object instance, String fieldName) {
        return getPrivateFieldValue(clazz, instance, fieldName, false);
    }

    /**
     * 获取 类中声明的私有 target 字段值
     *
     * @param clazz     类
     * @param instance  实例
     * @param fieldName 字段名称
     */
    public static Object getPrivateFieldValue(Class<?> clazz, Object instance, String fieldName, boolean parent) {
        Class<?> cur = clazz;
        do {
            try {
                Field field = cur.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object o = field.get(instance);
                if (o != null) return o;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                if (!parent) {
                    LOGGER.error("Failed to get private field {} from {}", fieldName, cur.getName(), e);
                }
            }
            cur = cur.getSuperclass();
        } while (parent && cur != Object.class);
        return null;
    }

}
