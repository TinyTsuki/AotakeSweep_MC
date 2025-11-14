package xin.vanilla.aotake.util;

import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("sunapi")
public class FieldUtils {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String UNSAFE_FIELD_NAME;
    private static final Unsafe UNSAFE;

    static {
        try {
            List<String> names = getPrivateFieldNames(Unsafe.class, Unsafe.class);
            if (CollectionUtils.isNotNullOrEmpty(names)) {
                UNSAFE_FIELD_NAME = names.get(0);
            } else {
                UNSAFE_FIELD_NAME = "theUnsafe";
            }
            UNSAFE = (Unsafe) getPrivateFieldValue(Unsafe.class, null, UNSAFE_FIELD_NAME);
        } catch (Exception e) {
            throw new RuntimeException("Unable to access Unsafe instance", e);
        }
    }

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

    /**
     * 设置 类中声明的私有 target 字段值 (支持private+final+static)
     *
     * @param clazz     类
     * @param instance  实例 (若为static字段应传null)
     * @param fieldName 字段名称
     * @param value     字段值
     */
    public static void setPrivateFieldValue(Class<?> clazz, Object instance, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);

            if (Modifier.isStatic(field.getModifiers())) {
                setStaticFieldByUnsafe(field, value);
            } else {
                setInstanceFieldByUnsafe(instance, field, value);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to set private field {} from {}", fieldName, clazz.getName(), e);
        }
    }

    private static void setInstanceFieldByUnsafe(Object instance, Field field, Object value) {
        long offset = UNSAFE.objectFieldOffset(field);
        UNSAFE.putObject(instance, offset, value);
    }

    private static void setStaticFieldByUnsafe(Field field, Object value) {
        Object base = UNSAFE.staticFieldBase(field);
        long offset = UNSAFE.staticFieldOffset(field);
        UNSAFE.putObject(base, offset, value);
    }

    public static Object newInstanceFromClassName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOGGER.error("Failed to create instance of class {}", className, e);
        }
        return null;
    }

    private static String LANGUAGE_FIELD_NAME;

    /**
     * 获取玩家语言字段名称
     */
    public static String getPlayerLanguageFieldName(ServerPlayer player) {
        if (StringUtils.isNotNullOrEmpty(LANGUAGE_FIELD_NAME)) return LANGUAGE_FIELD_NAME;
        try {
            for (String field : FieldUtils.getPrivateFieldNames(ServerPlayer.class, String.class)) {
                String lang = (String) FieldUtils.getPrivateFieldValue(ServerPlayer.class, player, field);
                if (StringUtils.isNotNullOrEmpty(lang) && lang.matches("^[a-zA-Z]{2}_[a-zA-Z]{2}$")) {
                    LANGUAGE_FIELD_NAME = field;
                }
            }
        } catch (Exception e) {
            LANGUAGE_FIELD_NAME = "language";
            LOGGER.error("Failed to get player language field name", e);
        }
        LOGGER.debug("Player language field name: {}", LANGUAGE_FIELD_NAME);
        return LANGUAGE_FIELD_NAME;
    }

}
