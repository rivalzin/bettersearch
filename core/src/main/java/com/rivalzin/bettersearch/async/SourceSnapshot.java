package com.rivalzin.bettersearch.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class SourceSnapshot<T> {
    private List<T> snapshot = Collections.emptyList();
    private Object owner;

    public synchronized List<T> capture(Collection<? extends T> source) {
        return capture(null, source);
    }

    public synchronized List<T> capture(Object owner, Collection<? extends T> source) {
        if (this.owner != owner) {
            this.owner = owner;
            snapshot = Collections.emptyList();
        }
        if (source.size() == snapshot.size()) {
            int position = 0;
            boolean unchanged = true;
            for (T value : source) {
                if (position >= snapshot.size() || value != snapshot.get(position++)) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged && position == snapshot.size()) {
                return snapshot;
            }
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(source));
        return snapshot;
    }

    public synchronized void clear() {
        snapshot = Collections.emptyList();
        owner = null;
    }
}
