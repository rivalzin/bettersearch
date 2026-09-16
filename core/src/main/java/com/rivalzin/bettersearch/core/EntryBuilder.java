package com.rivalzin.bettersearch.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EntryBuilder<T> {
    private final T value;
    private final List<SearchField> fields = new ArrayList<>(4);
    private final Set<String> seen = new HashSet<>(8);
    private String modId = "";
    private String family = "";

    public EntryBuilder(T value) {
        this.value = value;
    }

    private EntryBuilder<T> addNormalized(String normalized, byte source) {
        if (normalized != null && !normalized.isEmpty() && seen.add(source + ":" + normalized)) {
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

    public EntryBuilder<T> family(String registryPath) {
        this.family = familyOf(registryPath);
        return this;
    }

    public static String familyOf(String registryPath) {
        if (registryPath == null) {
            return "";
        }

        int end = registryPath.length();
        int cut = registryPath.lastIndexOf('_', end - 1);
        while (cut >= 0 && allDigits(registryPath, cut + 1, end)) {
            end = cut;
            cut = registryPath.lastIndexOf('_', end - 1);
        }
        String tail = cut >= 0 && cut + 1 < end
                ? registryPath.substring(cut + 1, end)
                : registryPath.substring(0, end);

        if (tail.isEmpty()) {
            tail = registryPath;
        }
        return TextNormalizer.normalize(tail);
    }

    private static boolean allDigits(String text, int from, int to) {
        if (from >= to) {
            return false;
        }
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public SearchIndex.Entry<T> build() {
        return new SearchIndex.Entry<>(value, fields.toArray(new SearchField[0]), modId, family);
    }
}
