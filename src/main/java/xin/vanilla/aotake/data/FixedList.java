package xin.vanilla.aotake.data;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FixedList<E> {
    private final int maxSize;
    private final LinkedList<E> list = new LinkedList<>();

    public FixedList(int maxSize) {
        this.maxSize = maxSize;
    }

    public synchronized void add(E e) {
        if (list.size() >= maxSize) {
            list.removeFirst();
        }
        list.addLast(e);
    }

    public synchronized List<E> asList() {
        return Collections.unmodifiableList(new LinkedList<>(list));
    }

    public synchronized E getLast() {
        return list.isEmpty() ? null : list.getLast();
    }

    public synchronized E getFirst() {
        return list.isEmpty() ? null : list.getFirst();
    }

    public synchronized E get(int index) {
        return (index >= 0 && index < list.size()) ? list.get(index) : null;
    }

    public synchronized int size() {
        return list.size();
    }

    public synchronized boolean isEmpty() {
        return list.isEmpty();
    }

    public synchronized void clear() {
        list.clear();
    }

    @Override
    public synchronized String toString() {
        return list.toString();
    }
}
