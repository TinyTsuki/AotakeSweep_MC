package xin.vanilla.aotake.data;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class KeyValue<K, V> {
    private K key;
    private V value;

    public KeyValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getLeft() {
        return this.key;
    }

    public V getRight() {
        return this.value;
    }
}
