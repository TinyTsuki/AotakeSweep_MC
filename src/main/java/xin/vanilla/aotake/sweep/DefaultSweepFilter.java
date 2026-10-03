package xin.vanilla.aotake.sweep;

import java.util.*;
import java.util.function.Function;

/** Shared two-stage selection for ordinary queries and the default sweep policy. */
public final class DefaultSweepFilter<E, K> {
    private final int typeLimit, safeLimit;
    private final Function<E, Entry<E, K>> inspect;

    public DefaultSweepFilter(int typeLimit, int safeLimit, Function<E, Entry<E, K>> inspect) {
        this.typeLimit = typeLimit;
        this.safeLimit = safeLimit;
        this.inspect = Objects.requireNonNull(inspect);
    }

    public List<E> select(List<E> candidates) {
        List<Entry<E, K>> scanned = new ArrayList<>(candidates.size());
        for (E candidate : candidates) {
            Entry<E, K> entry = inspect.apply(candidate);
            if (entry != null) scanned.add(entry);
        }
        return selectEntries(scanned, typeLimit, safeLimit);
    }

    public static <E, K> List<E> selectEntries(List<Entry<E, K>> scanned, int typeLimit, int safeLimit) {
        boolean countTypes = typeLimit < scanned.size(), countSafe = safeLimit < scanned.size();
        Map<String, Integer> typeCounts = new HashMap<>();
        Map<K, Integer> safeCounts = new HashMap<>();
        if (countTypes || countSafe) scanned.forEach(entry -> {
            if (countTypes && !entry.junk) typeCounts.merge(entry.type, 1, Integer::sum);
            if (countSafe && entry.safe) safeCounts.merge(entry.chunk, 1, Integer::sum);
        });
        List<E> selected = new ArrayList<>();
        scanned.forEach(entry -> {
            if ((!entry.safe && entry.junk)
                    || (countTypes && !entry.junk && typeCounts.get(entry.type) > typeLimit)
                    || (countSafe && entry.safe && safeCounts.get(entry.chunk) > safeLimit)) selected.add(entry.entity);
        });
        return selected;
    }

    public static final class Entry<E, K> {
        private final E entity;
        private final boolean safe, junk;
        private final String type;
        private final K chunk;
        public Entry(E entity, boolean safe, boolean junk, String type, K chunk) {
            this.entity = Objects.requireNonNull(entity);
            this.safe = safe; this.junk = junk; this.type = type; this.chunk = chunk;
        }
    }
}
