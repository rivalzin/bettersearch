package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// built once per item at index time, never during a search
public final class EntryBuilder<T> {
    private final T value;
    private final List<SearchField> fields = new ArrayList<>(4);
    private final Set<String> seen = new HashSet<>(8);
    private String modId = "";
    private String family = "";

    public EntryBuilder(T value) {
        this.value = value;
    }

    // only add() reaches this: everything the mod indexes arrives raw
    private EntryBuilder<T> addNormalized(String normalized, byte source) {
        if (normalized != null && !normalized.isEmpty() && seen.add(normalized)) {
            fields.add(new SearchField(normalized, source));
        }
        return this;
    }

    public EntryBuilder<T> add(String rawText, byte source) {
        return addNormalized(TextNormalizer.normalize(rawText), source);
    }

    public EntryBuilder<T> modId(String rawModId) {
        this.modId = TextNormalizer.normalize(rawModId);
        return this;
    }

    /**
     * What kind of thing this is: netherite_boots and leather_boots are both boots. Taken from
     * the registry name and not from the display name, because the id reads the same in every
     * language - a mod that ships no translation still lands beside its vanilla neighbours.
     */
    public EntryBuilder<T> family(String registryPath) {
        this.family = familyOf(registryPath);
        return this;
    }

    static String familyOf(String registryPath) {
        if (registryPath == null) {
            return "";
        }
        int cut = registryPath.lastIndexOf('_');
        String tail = cut >= 0 && cut + 1 < registryPath.length()
                ? registryPath.substring(cut + 1)
                : registryPath;
        return TextNormalizer.normalize(tail);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public SearchIndex.Entry<T> build() {
        return new SearchIndex.Entry<>(value, fields.toArray(new SearchField[0]), modId, family);
    }
}
