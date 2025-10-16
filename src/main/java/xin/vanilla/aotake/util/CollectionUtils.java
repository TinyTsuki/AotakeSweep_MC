package xin.vanilla.aotake.util;


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
     *
     * @param source 源集合
     * @param size   每个子集合的大小
     */
    public static <T> List<List<T>> splitToCollections(Collection<T> source, int size) {
        return splitToCollections(source, size, 0);
    }

    /**
     * 将集合根据指定数量分成多个子集合
     * <p>
     * 若根据 size 拆分后的组数超过 limit，则忽略 size，
     * 改为根据 limit 均匀拆分为 limit 组。
     *
     * @param source 源集合
     * @param size   每个子集合的大小
     * @param limit  最大子集合数量，0 为不限制
     */
    public static <T> List<List<T>> splitToCollections(Collection<T> source, int size, int limit) {
        if (source == null || source.isEmpty() || size <= 0)
            return Collections.emptyList();

        List<T> list = (source instanceof List)
                ? (List<T>) source
                : new ArrayList<>(source);

        int total = list.size();
        int chunkCount = (total + size - 1) / size;

        if (limit <= 0 || chunkCount <= limit) {
            return IntStream.range(0, chunkCount)
                    .mapToObj(i -> list.subList(i * size, Math.min(total, (i + 1) * size)))
                    .collect(Collectors.toList());
        }

        return IntStream.range(0, limit)
                .mapToObj(i -> {
                    int start = total * i / limit;
                    int end = total * (i + 1) / limit;
                    return list.subList(start, end);
                })
                .collect(Collectors.toList());
    }

    /**
     * 将集合根据指定数量分成多个子数组
     */
    public static <T> List<T[]> splitToArrays(Collection<T> source, int size) {
        return splitToArrays(source, size, 0);
    }

    /**
     * 将集合根据指定数量分成多个子数组
     * <p>
     * 若根据 size 拆分后的组数超过 limit，则忽略 size，
     * 改为根据 limit 均匀拆分为 limit 组。
     *
     * @param source 源集合
     * @param size   每个子集合的大小
     * @param limit  最大子集合数量，0 为不限制
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T[]> splitToArrays(Collection<T> source, int size, int limit) {
        if (source == null || source.isEmpty() || size <= 0) {
            return Collections.emptyList();
        }

        List<T> list = (source instanceof List) ? (List<T>) source : new ArrayList<>(source);
        int total = list.size();

        T[] full = list.toArray((T[]) new Object[0]);

        int chunkCount = (total + size - 1) / size;

        if (limit <= 0 || chunkCount <= limit) {
            return IntStream.range(0, chunkCount)
                    .mapToObj(i -> Arrays.copyOfRange(full, i * size, Math.min(total, (i + 1) * size)))
                    .collect(Collectors.toList());
        } else {
            return IntStream.range(0, limit)
                    .mapToObj(i -> Arrays.copyOfRange(full, total * i / limit, total * (i + 1) / limit))
                    .collect(Collectors.toList());
        }
    }

    /**
     * 将数组根据指定数量分成多个子集合
     */
    public static <T> List<List<T>> splitToCollections(T[] array, int size) {
        return splitToCollections(array, size, 0);
    }

    /**
     * 将数组根据指定数量分成多个子集合
     * <p>
     * 若根据 size 拆分后的组数超过 limit，则忽略 size，
     * 改为根据 limit 均匀拆分为 limit 组。
     *
     * @param array 源数组
     * @param size  每个子集合的大小
     * @param limit 最大子集合数量，0 为不限制
     */
    public static <T> List<List<T>> splitToCollections(T[] array, int size, int limit) {
        return splitToCollections(Arrays.asList(array), size, limit);
    }

    /**
     * 将数组根据指定数量分成多个子数组
     */
    public static <T> List<T[]> splitToArrays(T[] array, int size) {
        return splitToArrays(array, size, 0);
    }

    /**
     * 将数组根据指定数量分成多个子数组
     * <p>
     * 若根据 size 拆分后的组数超过 limit，则忽略 size，
     * 改为根据 limit 均匀拆分为 limit 组。
     *
     * @param array 源数组
     * @param size  每个子集合的大小
     * @param limit 最大子集合数量，0 为不限制
     */
    public static <T> List<T[]> splitToArrays(T[] array, int size, int limit) {
        return splitToArrays(Arrays.asList(array), size, limit);
    }

}
