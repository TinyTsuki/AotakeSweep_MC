package xin.vanilla.aotake.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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
     * 获取 类中声明的私有 target 字段名称
     *
     * @param clazz  类
     * @param target 字段类型
     * @return 字段名称
     */
    public static List<String> getPrivateFieldNames(Class<?> clazz, Class<?> target) {
        return getPrivateFieldNames(clazz, target, false, false, false);
    }

    /**
     * 获取 类中声明的私有 target 字段名称
     *
     * @param clazz  类
     * @param target 字段类型
     * @return 字段名称
     */
    public static List<String> getPrivateFieldNames(Class<?> clazz, Class<?> target, boolean parent, boolean targetFrom, boolean targetInstance) {
        List<String> fieldNames = new ArrayList<>();
        Class<?> cur = clazz;
        try {
            do {
                Field[] fields = cur.getDeclaredFields();
                for (Field field : fields) {
                    if ((Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
                            && ((field.getType() == target) || (targetFrom && target.isAssignableFrom(field.getType())) || (targetInstance && target.isInstance(field.getType())))
                    ) {
                        fieldNames.add(field.getName());
                    }
                }
                cur = cur.getSuperclass();
            } while (parent && cur != Object.class);
        } catch (Exception e) {
            LOGGER.error("Failed to get private field names from {}", cur.getName(), e);
        }
        return fieldNames;
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
