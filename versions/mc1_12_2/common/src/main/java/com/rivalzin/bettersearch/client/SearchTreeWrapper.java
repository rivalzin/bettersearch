package com.rivalzin.bettersearch.client;

import net.minecraft.client.util.SearchTree;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class SearchTreeWrapper<T> extends SearchTree<T> {
    private final SearchTree<T> vanilla;
    private final Function<String, List<T>> finder;

    public SearchTreeWrapper(SearchTree<T> vanilla, Function<String, List<T>> finder) {
        super(value -> Collections.<String>emptyList(),
                value -> Collections.<ResourceLocation>emptyList());
        this.vanilla = vanilla;
        this.finder = finder;
    }

    @Override
    public void recalculate() {
        vanilla.recalculate();
    }

    @Override
    public void add(T value) {
        vanilla.add(value);
    }

    @Override
    // null from us = fall back to vanilla instead of showing nothing
    public List<T> search(String query) {
        List<T> ours = finder.apply(query);
        return ours != null ? ours : vanilla.search(query);
    }
}
