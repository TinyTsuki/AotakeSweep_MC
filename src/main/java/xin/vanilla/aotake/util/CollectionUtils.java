package xin.vanilla.aotake.util;


import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class CollectionUtils {


    public static boolean isNullOrEmpty(Collection<?> list) {
        return list == null || list.isEmpty();
    }

    public static boolean isNotNullOrEmpty(Collection<?> list) {
        return !isNullOrEmpty(list);
    }

    public static boolean isNullOrEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isNotNullOrEmpty(Object[] array) {
        return !isNullOrEmpty(array);
    }

    public static boolean isNullOrEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 从给定的集合中随机选取一个元素。
     *
     * @param <T>      集合中元素的类型
     * @param elements 要从中选取随机元素的集合
     * @return 随机选取的元素
     */
    public static <T> T getRandomElement(T[] elements) {
        return getRandomElement(elements, ThreadLocalRandom.current());
    }

    /**
     * 从给定的集合中随机选取一个元素。
     *
     * @param <T>      集合中元素的类型
     * @param elements 要从中选取随机元素的集合
     * @param random   用于生成随机数的随机数生成器
     * @return 随机选取的元素
     */
    public static <T> T getRandomElement(T[] elements, Random random) {
        if (elements == null || elements.length == 0) {
            return null;
        }
        int index = random.nextInt(elements.length);
        return elements[index];
    }

    /**
     * 从给定的集合中随机选取一个元素。
     *
     * @param <T>      集合中元素的类型
     * @param elements 要从中选取随机元素的集合
     * @return 随机选取的元素
     */
    public static <T> T getRandomElement(Collection<T> elements) {
        return getRandomElement(elements, ThreadLocalRandom.current());
    }

    /**
     * 从给定的集合中随机选取一个元素。
     *
     * @param <T>      集合中元素的类型
     * @param elements 要从中选取随机元素的集合
     * @param random   用于生成随机数的随机数生成器
     * @return 随机选取的元素
     */
    public static <T> T getRandomElement(Collection<T> elements, Random random) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }

        int index = random.nextInt(elements.size());
        return getNthElement(elements, index);
    }

    /**
     * 从给定的集合中取第一个元素
     */
    public static <T> T getFirst(Collection<T> elements) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }

        return getNthElement(elements, 0);
    }

    /**
     * 从给定的集合中取第一个元素
     */
    public static <T> T getFirst(T[] elements) {
        if (elements == null || elements.length == 0) {
            return null;
        }

        return elements[0];
    }

    /**
     * 从给定的集合中取最后一个元素
     */
    public static <T> T getLast(Collection<T> elements) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }

        return getNthElement(elements, elements.size() - 1);
    }

    /**
     * 从给定的集合中取最后一个元素
     */
    public static <T> T getLast(T[] elements) {
        if (elements == null || elements.length == 0) {
            return null;
        }

        return elements[elements.length - 1];
    }

    public static <T> T getOrDefault(Collection<T> elements, int index, T defaultValue) {
        if (elements == null || elements.isEmpty() || index >= elements.size()) {
            return defaultValue;
        }
        return getNthElement(elements, index);
    }

    public static <T> T getOrDefault(T[] elements, int index, T defaultValue) {
        if (elements == null || elements.length == 0 || index >= elements.length) {
            return defaultValue;
        }
        return elements[index];
    }

    /**
     * 获取集合中指定索引位置的元素。
     *
     * @param <T>      集合中元素的类型
     * @param elements 要从中获取元素的集合
     * @param index    要获取的元素的索引位置
     * @return 指定索引位置的元素
     */
    private static <T> T getNthElement(Collection<T> elements, int index) {
        int currentIndex = 0;
        for (T element : elements) {
            if (currentIndex == index) {
                return element;
            }
            currentIndex++;
        }
        // This should never happen due to the size check in getRandomElement.
        throw new IllegalStateException("Could not find element at the specified index.");
    }
}
