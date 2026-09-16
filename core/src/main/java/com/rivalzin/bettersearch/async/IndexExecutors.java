package com.rivalzin.bettersearch.async;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class IndexExecutors {
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "BetterSearch-Capture");
        thread.setDaemon(true);
        return thread;
    });

    private IndexExecutors() {
    }

    public static Executor yielding() {
        return task -> TIMER.schedule(task, 8, TimeUnit.MILLISECONDS);
    }
}
