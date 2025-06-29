package xin.vanilla.aotake.data;

import lombok.NonNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConcurrentShuffleList<T> implements Iterable<T> {
    private final List<T> list = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void add(T item) {
        lock.lock();
        try {
            list.add(item);
        } finally {
            lock.unlock();
        }
    }

    public void addAll(Collection<? extends T> items) {
        lock.lock();
        try {
            list.addAll(items);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(T element) {
        lock.lock();
        try {
            return list.remove(element);
        } finally {
            lock.unlock();
        }
    }

    public T removeRandom() {
        lock.lock();
        try {
            if (list.isEmpty()) return null;
            int index = ThreadLocalRandom.current().nextInt(list.size());
            return list.remove(index);
        } finally {
            lock.unlock();
        }
    }

    public T removeIf(Predicate<T> predicate) {
        lock.lock();
        try {
            for (int i = 0; i < list.size(); i++) {
                if (predicate.test(list.get(i))) {
                    return list.remove(i);
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public List<T> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(list);
        } finally {
            lock.unlock();
        }
    }

    public Stream<T> stream() {
        return snapshot().stream();
    }

    public Stream<T> parallelStream() {
        return snapshot().parallelStream();
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int cursor = 0;
            private int lastRet = -1;

            @Override
            public boolean hasNext() {
                lock.lock();
                try {
                    return cursor < list.size();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public T next() {
                lock.lock();
                try {
                    if (cursor >= list.size()) throw new NoSuchElementException();
                    lastRet = cursor;
                    return list.get(cursor++);
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void remove() {
                lock.lock();
                try {
                    if (lastRet < 0) throw new IllegalStateException();
                    list.remove(lastRet);
                    cursor = lastRet;
                    lastRet = -1;
                } finally {
                    lock.unlock();
                }
            }
        };
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return list.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            list.clear();
        } finally {
            lock.unlock();
        }
    }
}
