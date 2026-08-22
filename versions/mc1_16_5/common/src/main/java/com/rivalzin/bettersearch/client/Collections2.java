package com.rivalzin.bettersearch.client;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Collections2 {
    private Collections2() {
    }

    @SafeVarargs
    static <T> List<T> list(T... items) {
        return items.length == 0
                ? Collections.<T>emptyList()
                : Collections.unmodifiableList(Arrays.asList(items));
    }

    @SafeVarargs
    // java 8 target: no Set.of / Map.of here
    static <T> Set<T> setOf(T... items) {
        if (items.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(items)));
    }

    static <K, V> Map<K, V> emptyMap() {
        return Collections.emptyMap();
    }

    static <K, V> Map.Entry<K, V> pair(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<K, V>(key, value);
    }

    @SafeVarargs
    static <K, V> Map<K, V> map(Map.Entry<K, V>... pairs) {
        Map<K, V> out = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> pair : pairs) {
            out.put(pair.getKey(), pair.getValue());
        }
        return Collections.unmodifiableMap(out);
    }
}
