package com.rivalzin.bettersearch.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class StorageInventory<T> {
    private Map<T, T> entries = Collections.emptyMap();
    private Map<Object, T> aliases = Collections.emptyMap();
    private List<T> values = Collections.emptyList();

    public void update(List<?> source, Function<Object, T> key, Function<T, T> snapshot) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(snapshot, "snapshot");
        Map<T, T> nextEntries = new HashMap<>();
        Map<Object, T> nextAliases = new IdentityHashMap<>(source.size());
        boolean added = false;
        for (Object value : source) {
            T lookup = key.apply(value);
            if (lookup == null) {
                continue;
            }
            T stable = nextEntries.get(lookup);
            if (stable == null) {
                stable = entries.get(lookup);
                if (stable == null) {
                    stable = Objects.requireNonNull(snapshot.apply(lookup), "snapshot key");
                    if (!stable.equals(lookup) || stable.hashCode() != lookup.hashCode()) {
                        throw new IllegalArgumentException("Snapshot differs from lookup key");
                    }
                    added = true;
                }
                nextEntries.put(stable, stable);
            }
            nextAliases.put(value, stable);
        }
        if (added || nextEntries.size() != entries.size()) {
            values = Collections.unmodifiableList(new ArrayList<>(nextEntries.values()));
        }
        entries = nextEntries;
        aliases = nextAliases;
    }

    public T key(Object value) {
        return aliases.get(value);
    }

    public List<T> values() {
        return values;
    }

    public void clear() {
        entries = Collections.emptyMap();
        aliases = Collections.emptyMap();
        values = Collections.emptyList();
    }
}
