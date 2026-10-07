package com.rivalzin.bettersearch.compat;

import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ChestSearchCache<K> {
    private final int capacity;
    private final Map<K, Entry<K>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private SearchSettings settings;
    private SearchSettings sourceSettings;
    private long languageRevision;
    private String text;
    private SearchQuery query;
    private long revision;
    private boolean active;

    public ChestSearchCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Non-positive cache capacity");
        }
        this.capacity = capacity;
    }

    public void begin(String text, SearchSettings settings, long languageRevision) {
        Objects.requireNonNull(settings, "settings");
        boolean changedSettings = sourceSettings == null || !sourceSettings.equals(settings);
        if (sourceSettings == null || sourceSettings.affectsIndex(settings)
                || this.languageRevision != languageRevision) {
            entries.clear();
        }
        if (changedSettings || this.languageRevision != languageRevision || !Objects.equals(this.text, text)) {
            sourceSettings = settings.copy();
            this.settings = settings.copy();
            this.settings.maxResults = 1;
            this.settings.sortByRelevance = false;
            this.languageRevision = languageRevision;
            this.text = text;
            query = StorageSearchSession.isPlainTerm(text) ? SearchQuery.parse(text, this.settings) : null;
            revision++;
        }
        active = this.settings.enabled && query != null && !query.isEmpty();
    }

    public boolean matches(String text, K lookup, Supplier<K> snapshot,
                           Function<K, SearchIndex<K>> build) {
        if (!active || !Objects.equals(this.text, text) || lookup == null) {
            return false;
        }
        Entry<K> entry = entries.get(lookup);
        if (entry == null) {
            K key = Objects.requireNonNull(snapshot.get(), "snapshot");
            entry = new Entry<>(Objects.requireNonNull(build.apply(key), "index"));
            if (entries.size() == capacity) {
                entries.remove(entries.keySet().iterator().next());
            }
            entries.put(key, entry);
        }
        if (entry.revision != revision) {
            entry.matched = !entry.index.search(query, settings).isEmpty();
            entry.revision = revision;
        }
        return entry.matched;
    }

    public boolean active() {
        return active;
    }

    public void end() {
        active = false;
    }

    public void clear() {
        entries.clear();
        settings = null;
        sourceSettings = null;
        query = null;
        text = null;
        active = false;
    }

    private static final class Entry<K> {
        final SearchIndex<K> index;
        long revision = Long.MIN_VALUE;
        boolean matched;

        Entry(SearchIndex<K> index) {
            this.index = index;
        }
    }
}
