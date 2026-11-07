package com.rdh.swarm.task;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BaseMap<K, V> {

    private final Map<K, V> map;

    public BaseMap() {
        this.map = new ConcurrentHashMap<>();
    }

    public V put(K key, V value) {
        return map.put(key, value);
    }

    public V get(K key) {
        return map.get(key);
    }

    public V remove(K key) {
        return map.remove(key);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public Set<K> keySet() { return map.keySet(); }
    public Collection<V> values() { return map.values(); }
    public int size() { return map.size(); }
    public void clear() { map.clear(); }
}
