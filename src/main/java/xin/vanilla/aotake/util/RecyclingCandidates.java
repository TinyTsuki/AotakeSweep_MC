package xin.vanilla.aotake.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Batch-owned identity index; unprepared identities cannot be committed. */
final class RecyclingCandidates<E> {
    private enum State { SEEN, REJECTED, DISCARD, RECYCLE }
    private final IdentityHashMap<E, State> decisions = new IdentityHashMap<>();

    List<E> gather(List<E> subjects, Function<E, E> canonical, Predicate<E> eligible) {
        decisions.clear();
        List<E> result = new ArrayList<>(subjects.size());
        for (E subject : subjects) {
            E entity = canonical.apply(subject);
            State previous = decisions.put(entity, State.SEEN);
            if (previous != null) {
                if (previous != State.SEEN) decisions.put(entity, previous);
                continue;
            }
            if (eligible.test(entity)) result.add(entity);
            else decisions.put(entity, State.REJECTED);
        }
        return result;
    }

    void decide(E entity, boolean recycle) {
        if (decisions.get(entity) != State.SEEN) throw new IllegalArgumentException("Unprepared recycling identity");
        decisions.put(entity, recycle ? State.RECYCLE : State.DISCARD);
    }
    boolean ready(E entity) {
        State state = decisions.get(entity);
        return state == State.RECYCLE || state == State.DISCARD;
    }
    boolean recycle(E entity) {
        State state = decisions.get(entity);
        if (state == State.RECYCLE) return true;
        if (state == State.DISCARD) return false;
        throw new IllegalStateException("Recycling identity has no decision");
    }
    void clear() { decisions.clear(); }
}
