package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.core.SearchIndex;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

public final class AsyncIndex<T> {
    private final AsyncIndexState<SearchIndex<T>> state;

    public AsyncIndex(String name) {
        state = new AsyncIndexState<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task),
                task -> Minecraft.getInstance().execute(task),
                error -> BetterSearch.LOGGER.error("[{}] failed to build {} index",
                        BetterSearch.MOD_NAME, name, error));
    }

    public SearchIndex<T> ready(Object source, int size, long stamp) {
        return state.ready(source, size, stamp);
    }

    public SearchIndex<T> peek() {
        return state.peek();
    }

    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build) {
        return get(source, size, stamp, build, null);
    }

    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build,
                              Runnable onReady) {
        return state.get(source, size, stamp, build, onReady);
    }

    public SearchIndex<T> getPrepared(Object source, int size, long stamp,
                                     Supplier<Supplier<SearchIndex<T>>> prepare, Runnable onReady) {
        return state.getPrepared(source, size, stamp, prepare, onReady);
    }

    public SearchIndex<T> getPrepared(Object source, int size, long stamp,
                                     Supplier<Supplier<SearchIndex<T>>> prepare) {
        return getPrepared(source, size, stamp, prepare, null);
    }

    public void invalidate() {
        state.invalidate();
    }
}
