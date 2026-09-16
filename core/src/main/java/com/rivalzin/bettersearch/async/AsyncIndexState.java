package com.rivalzin.bettersearch.async;

import com.rivalzin.bettersearch.FailurePolicy;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AsyncIndexState<V> {
    private final Executor prepareExecutor;
    private final Executor workerExecutor;
    private final Executor completionExecutor;
    private final Executor continuationExecutor;
    private final Consumer<Throwable> errorHandler;
    private final LongSupplier clock;
    private final long retryDelay;
    private volatile Snapshot<V> published;
    private long generation;
    private volatile Request<V> active;
    private Request<V> queued;
    private Request<V> failed;
    private long failedAt;

    public AsyncIndexState(Executor prepareExecutor, Executor workerExecutor,
                           Executor completionExecutor, Consumer<Throwable> errorHandler) {
        this(prepareExecutor, workerExecutor, completionExecutor, errorHandler,
                IndexExecutors.yielding(), System::nanoTime, TimeUnit.SECONDS.toNanos(1));
    }

    public AsyncIndexState(Executor prepareExecutor, Executor workerExecutor,
                           Executor completionExecutor, Consumer<Throwable> errorHandler,
                           LongSupplier clock, long retryDelay) {
        this(prepareExecutor, workerExecutor, completionExecutor, errorHandler,
                prepareExecutor, clock, retryDelay);
    }

    public AsyncIndexState(Executor prepareExecutor, Executor workerExecutor,
                           Executor completionExecutor, Consumer<Throwable> errorHandler,
                           Executor continuationExecutor, LongSupplier clock, long retryDelay) {
        this.prepareExecutor = Objects.requireNonNull(prepareExecutor);
        this.workerExecutor = Objects.requireNonNull(workerExecutor);
        this.completionExecutor = Objects.requireNonNull(completionExecutor);
        this.continuationExecutor = Objects.requireNonNull(continuationExecutor);
        this.errorHandler = Objects.requireNonNull(errorHandler);
        this.clock = Objects.requireNonNull(clock);
        if (retryDelay < 0) {
            throw new IllegalArgumentException("Negative retry delay");
        }
        this.retryDelay = retryDelay;
    }

    public V ready(Object source, int size, long stamp) {
        Snapshot<V> snapshot = published;
        if (snapshot == null || !snapshot.matches(source, size, stamp)) {
            return null;
        }
        if (active != null) {
            synchronized (this) {
                if (published != snapshot) {
                    return null;
                }
                generation++;
                queued = null;
            }
        }
        return snapshot.value;
    }

    public V peek() {
        Snapshot<V> snapshot = published;
        return snapshot == null ? null : snapshot.value;
    }

    public V get(Object source, int size, long stamp, Supplier<V> build, Runnable onReady) {
        Objects.requireNonNull(build);
        return getPrepared(source, size, stamp, () -> build, onReady);
    }

    public V getPrepared(Object source, int size, long stamp,
                         Supplier<Supplier<V>> prepare, Runnable onReady) {
        Objects.requireNonNull(prepare);
        Request<V> start;
        synchronized (this) {
            V current = ready(source, size, stamp);
            if (current != null) {
                queued = null;
                return current;
            }
            if (failed != null && failed.matches(source, size, stamp)
                    && clock.getAsLong() - failedAt < retryDelay) {
                return null;
            }
            if (active != null && active.generation == generation
                    && active.matches(source, size, stamp)) {
                queued = null;
                return null;
            }
            if (queued != null && queued.matches(source, size, stamp)) {
                return null;
            }
            Request<V> request = new Request<>(source, size, stamp, generation, prepare, onReady);
            if (active != null) {
                queued = request;
                return null;
            }
            active = request;
            start = request;
        }
        schedule(start);
        return ready(source, size, stamp);
    }

    public synchronized void invalidate() {
        generation++;
        published = null;
        queued = null;
        failed = null;
    }

    private synchronized boolean shouldRun(Request<V> request) {
        return active == request && generation == request.generation && queued == null;
    }

    private void schedule(Request<V> request) {
        execute(prepareExecutor, request, () -> {
            if (!shouldRun(request)) {
                finish(request, null, null);
                return;
            }
            Supplier<V> build = Objects.requireNonNull(request.prepare.get(), "Missing index builder");
            request.prepare = null;
            advance(request, build);
        });
    }

    private void advance(Request<V> request, Supplier<V> build) {
        if (!shouldRun(request)) {
            finish(request, null, null);
            return;
        }
        if (build instanceof StagedSupplier && !((StagedSupplier<?>) build).advance()) {
            execute(continuationExecutor, request,
                    () -> execute(prepareExecutor, request, () -> advance(request, build)));
            return;
        }
        execute(workerExecutor, request, () -> {
            if (!shouldRun(request)) {
                finish(request, null, null);
                return;
            }
            V value = Objects.requireNonNull(build.get(), "Missing index");
            execute(completionExecutor, request, () -> finish(request, value, null));
        });
    }

    private void execute(Executor executor, Request<V> request, Runnable action) {
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException error) {
                    fail(request, error);
                } catch (Error error) {
                    abandon(request);
                    throw error;
                }
            });
        } catch (RuntimeException error) {
            fail(request, error);
        } catch (Error error) {
            abandon(request);
            throw error;
        }
    }

    private void fail(Request<V> request, RuntimeException error) {
        try {
            FailurePolicy.rethrowFatal(error);
        } catch (Error fatal) {
            abandon(request);
            throw fatal;
        }
        finish(request, null, error);
    }

    private synchronized void abandon(Request<V> request) {
        if (active == request) {
            active = null;
            queued = null;
            request.prepare = null;
            request.onReady = null;
        }
    }

    private void finish(Request<V> request, V value, RuntimeException error) {
        Request<V> next;
        Runnable callback = null;
        boolean report = false;
        synchronized (this) {
            if (active != request) {
                return;
            }
            boolean accepted = request.generation == generation && queued == null;
            if (accepted && error != null) {
                failed = request;
                failedAt = clock.getAsLong();
                report = true;
            } else if (accepted && value != null) {
                published = new Snapshot<>(request.source, request.size, request.stamp, value);
                failed = null;
                callback = request.onReady;
            }
            request.prepare = null;
            request.onReady = null;
            next = queued;
            queued = null;
            active = next;
        }
        try {
            if (report) {
                errorHandler.accept(error);
            }
            if (callback != null) {
                try {
                    callback.run();
                } catch (RuntimeException callbackError) {
                    FailurePolicy.rethrowFatal(callbackError);
                    errorHandler.accept(callbackError);
                }
            }
        } finally {
            if (next != null) {
                schedule(next);
            }
        }
    }

    private static class Key {
        final Object source;
        final int size;
        final long stamp;

        Key(Object source, int size, long stamp) {
            this.source = source;
            this.size = size;
            this.stamp = stamp;
        }

        final boolean matches(Object source, int size, long stamp) {
            return this.source == source && this.size == size && this.stamp == stamp;
        }
    }

    private static final class Request<V> extends Key {
        final long generation;
        Supplier<Supplier<V>> prepare;
        Runnable onReady;

        Request(Object source, int size, long stamp, long generation,
                Supplier<Supplier<V>> prepare, Runnable onReady) {
            super(source, size, stamp);
            this.generation = generation;
            this.prepare = prepare;
            this.onReady = onReady;
        }
    }

    private static final class Snapshot<V> extends Key {
        final V value;

        Snapshot(Object source, int size, long stamp, V value) {
            super(source, size, stamp);
            this.value = value;
        }
    }
}
