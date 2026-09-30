package xin.vanilla.aotake.sweep;

import xin.vanilla.aotake.data.ChunkKey;
import xin.vanilla.aotake.util.ChunkCleanupPolicy;
import java.util.*;
import java.util.function.Function;

/** Keeps notice counts separate from the candidates removed after applying the retain ratio. */
public final class ChunkSweepSelection<E> {
    private final Map<ChunkKey,List<E>> groups;
    private final List<E> selected;
    private ChunkSweepSelection(Map<ChunkKey,List<E>> groups, List<E> selected) {
        this.groups=Collections.unmodifiableMap(groups); this.selected=Collections.unmodifiableList(selected);
    }
    public static <E> ChunkSweepSelection<E> select(List<E> entities, Function<E,ChunkKey> key, int limit, double retain) {
        Map<ChunkKey,List<E>> all=new LinkedHashMap<>(), groups=new LinkedHashMap<>();
        for(E entity:entities) all.computeIfAbsent(key.apply(entity),ignored->new ArrayList<>()).add(entity);
        List<E> selected=new ArrayList<>();
        all.forEach((chunk, entries)-> {
            if(entries.size()<=limit) return;
            groups.put(chunk,Collections.unmodifiableList(entries));
            selected.addAll(entries.subList(ChunkCleanupPolicy.retainedCount(entries.size(),limit,retain),entries.size()));
        });
        return new ChunkSweepSelection<>(groups,selected);
    }
    public Map<ChunkKey,List<E>> groups(){ return groups; }
    public List<E> selected(){ return selected; }
}
