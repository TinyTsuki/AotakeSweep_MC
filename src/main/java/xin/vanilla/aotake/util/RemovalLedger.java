package xin.vanilla.aotake.util;

import java.util.*;
import java.util.function.BiPredicate;

/** Owner-thread ledger; ownership survives failed removal and rule-generation changes. */
public final class RemovalLedger<K,S> {
    private final Map<K,Queue<S>> queues = new HashMap<>();
    private final Map<S,Boolean> owned = new IdentityHashMap<>();

    public boolean schedule(K world, S subject, boolean keepData) {
        Boolean previous = owned.get(subject);
        if (previous != null) {
            if (keepData) owned.put(subject, true);
            return false;
        }
        owned.put(subject, keepData);
        queues.computeIfAbsent(world, ignored -> new ArrayDeque<>()).add(subject);
        return true;
    }

    public boolean contains(S subject) { return owned.containsKey(subject); }

    public void flush(K world, BiPredicate<S,Boolean> remove) {
        Queue<S> queue = queues.get(world);
        if (queue == null) return;
        // A failed entity is retried on a later tick, never repeatedly in this flush.
        int count = queue.size();
        for (int i = 0; i < count; i++) {
            S subject = queue.remove();
            boolean removed = false;
            try { removed = remove.test(subject, owned.get(subject)); }
            finally {
                if (removed) owned.remove(subject);
                else queue.add(subject);
            }
        }
        if (queue.isEmpty()) queues.remove(world);
    }

    public void clear() { queues.clear(); owned.clear(); }
}
