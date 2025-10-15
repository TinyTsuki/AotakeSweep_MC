package xin.vanilla.aotake.util;


import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    /**
     * 将集合根据指定数量分成多个子集合
     */
    public static <T> List<List<T>> splitToCollections(Collection<T> source, int size) {
        if (source == null || source.isEmpty() || size <= 0)
            return Collections.emptyList();

        List<T> list = new ArrayList<>(source);
        int total = list.size();
        return IntStream.range(0, (total + size - 1) / size)
                .mapToObj(i -> list.subList(i * size, Math.min(total, (i + 1) * size)))
                .collect(Collectors.toList());
    }

    /**
     * 将集合根据指定数量分成多个子数组
     */
    public static <T> List<T[]> splitToArrays(Collection<T> source, int size, Class<T> type) {
        if (source == null || source.isEmpty() || size <= 0)
            return Collections.emptyList();

        List<T> list = new ArrayList<>(source);
        int total = list.size();
        T[] array = list.toArray((T[]) Array.newInstance(type, total));

        return IntStream.range(0, (total + size - 1) / size)
                .mapToObj(i -> Arrays.copyOfRange(array, i * size, Math.min(total, (i + 1) * size)))
                .collect(Collectors.toList());
    }

    /**
     * 将数组根据指定数量分成多个子集合
     */
    public static <T> List<List<T>> splitToCollections(T[] array, int size) {
        if (array == null || array.length == 0 || size <= 0)
            return Collections.emptyList();

        int total = array.length;
        return IntStream.range(0, (total + size - 1) / size)
                .mapToObj(i -> Arrays.asList(Arrays.copyOfRange(array, i * size, Math.min(total, (i + 1) * size))))
                .collect(Collectors.toList());
    }

    /**
     * 将数组根据指定数量分成多个子数组
     */
    public static <T> List<T[]> splitToArrays(T[] array, int size) {
        if (array == null || array.length == 0 || size <= 0)
            return Collections.emptyList();

        int total = array.length;
        return IntStream.range(0, (total + size - 1) / size)
                .mapToObj(i -> Arrays.copyOfRange(array, i * size, Math.min(total, (i + 1) * size)))
                .collect(Collectors.toList());
    }

}
