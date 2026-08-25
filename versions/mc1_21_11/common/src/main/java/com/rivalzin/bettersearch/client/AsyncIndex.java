package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// built off-thread, swapped in whole - readers never see a half index
public final class AsyncIndex<T> {
    private final String name;

    private volatile SearchIndex<T> index;
    private volatile Object readySource;
    private volatile int readySize = -1;
    private volatile long readyStamp = Long.MIN_VALUE;

    private volatile Object pendingSource;
    private volatile int pendingSize = -1;
    private volatile long pendingStamp = Long.MIN_VALUE;

    private volatile boolean building;
    private volatile boolean failed;

    public AsyncIndex(String name) {
        this.name = name;
    }

    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build) {
        return get(source, size, stamp, build, null);
    }

    public SearchIndex<T> ready(Object source, int size, long stamp) {
        SearchIndex<T> current = index;
        if (current != null && readySource == source && readySize == size && readyStamp == stamp) {
            return current;
        }
        return null;
    }

    public SearchIndex<T> get(Object source, int size, long stamp, Supplier<SearchIndex<T>> build,
                              Runnable onReady) {
        SearchIndex<T> current = index;
        if (current != null && readySource == source && readySize == size && readyStamp == stamp) {
            return current;
        }
        if (failed) {
            return null;
        }
        if (!building && (pendingSource != source || pendingSize != size || pendingStamp != stamp)) {
            start(source, size, stamp, build, onReady);
        }
        return null;
    }

    private synchronized void start(Object source, int size, long stamp, Supplier<SearchIndex<T>> build,
                                    Runnable onReady) {
        if (building || (pendingSource == source && pendingSize == size && pendingStamp == stamp)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        building = true;
        pendingSource = source;
        pendingSize = size;
        pendingStamp = stamp;

        CompletableFuture
                .supplyAsync(build, Util.backgroundExecutor())
                .whenComplete((built, error) -> minecraft.execute(() -> {
                    building = false;
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] failed to build {} index",
                                BetterSearch.MOD_NAME, name, error);
                        failed = true;
                        return;
                    }
                    index = built;
                    readySource = pendingSource;
                    readySize = pendingSize;
                    readyStamp = pendingStamp;
                    if (onReady != null) {
                        onReady.run();
                    }
                }));
    }

    // drops the index, the next search rebuilds it
    public void invalidate() {
        // failed too, or one build failure keeps the index off all session
        failed = false;
        index = null;
        readySource = null;
        readySize = -1;
        readyStamp = Long.MIN_VALUE;

        pendingSource = null;
        pendingSize = -1;
        pendingStamp = Long.MIN_VALUE;
    }
}
