package com.rivalzin.bettersearch.async;

import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EntrySnapshot<T> {
    private final T value;
    private final List<String> texts = new ArrayList<>(4);
    private final List<Byte> sources = new ArrayList<>(4);
    private String modId;
    private String family;

    public EntrySnapshot(T value) {
        this.value = value;
    }

    public EntrySnapshot<T> add(String text, byte source) {
        if (text != null && !text.isEmpty()) {
            texts.add(text);
            sources.add(source);
        }
        return this;
    }

    public EntrySnapshot<T> modId(String modId) {
        this.modId = modId;
        return this;
    }

    public EntrySnapshot<T> family(String family) {
        this.family = family;
        return this;
    }

    public boolean isEmpty() {
        return texts.isEmpty();
    }

    public SearchIndex.Entry<T> build() {
        return normalized().build();
    }

    private EntryBuilder<T> normalized() {
        EntryBuilder<T> builder = new EntryBuilder<>(value).modId(modId).family(family);
        for (int i = 0; i < texts.size(); i++) {
            builder.add(texts.get(i), sources.get(i));
        }
        return builder;
    }

    public static <T> SearchIndex<T> index(List<EntrySnapshot<T>> snapshots) {
        return index(snapshots, true);
    }

    public static <T> SearchIndex<T> index(List<EntrySnapshot<T>> snapshots, boolean groupFamilies) {
        List<SearchIndex.Entry<T>> entries = new ArrayList<>(snapshots.size());
        for (EntrySnapshot<T> snapshot : snapshots) {
            EntryBuilder<T> builder = snapshot.normalized();
            if (!builder.isEmpty()) {
                entries.add(builder.build());
            }
        }
        return new SearchIndex<>(entries, groupFamilies);
    }

    public static <S, T> StagedSupplier<SearchIndex<T>> capture(List<S> source,
                                                               Function<S, EntrySnapshot<T>> capture) {
        return capture(source, capture, true);
    }

    public static <S, T> StagedSupplier<SearchIndex<T>> capture(List<S> source,
                                                               Function<S, EntrySnapshot<T>> capture,
                                                               boolean groupFamilies) {
        return new StagedSupplier<SearchIndex<T>>() {
            private final List<EntrySnapshot<T>> snapshots = new ArrayList<>(source.size());
            private int position;

            @Override
            public boolean advance() {
                long started = System.nanoTime();
                int limit = Math.min(source.size(), position + 256);
                while (position < limit) {
                    EntrySnapshot<T> entry = capture.apply(source.get(position++));
                    if (entry != null && !entry.isEmpty()) {
                        snapshots.add(entry);
                    }
                    if (System.nanoTime() - started >= 4_000_000L) {
                        break;
                    }
                }
                return position == source.size();
            }

            @Override
            public SearchIndex<T> get() {
                if (position != source.size()) {
                    throw new IllegalStateException("Index capture is incomplete");
                }
                return index(snapshots, groupFamilies);
            }
        };
    }
}
