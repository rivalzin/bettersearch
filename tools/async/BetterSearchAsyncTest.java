package com.rivalzin.bettersearch.tools;

import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StagedSupplier;
import com.rivalzin.bettersearch.async.SourceSnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class BetterSearchAsyncTest {
    private static int checks;

    public static void main(String[] args) {
        lifecycle();
        rejectionAndRetry();
        sourceKeys();
        registrySnapshots();
        batchCapture();
        concurrentRequests();
        fatalFailures();
        System.out.println("BetterSearchAsyncTest: " + checks + " checks passed");
    }

    private static void lifecycle() {
        Fixture f = new Fixture();
        Object source = new Object();
        AtomicInteger prepares = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        for (int i = 0; i < 200; i++) {
            f.index.getPrepared(source, 2, 1, () -> {
                prepares.incrementAndGet();
                return () -> "old";
            }, callbacks::incrementAndGet);
        }
        check(f.prepare.size() == 1, "Concurrent requests must reserve one preparation");
        f.prepare.runOne();
        check(prepares.get() == 1, "Expensive snapshots are only made once");
        f.worker.runOne();
        f.index.invalidate();
        f.index.get(source, 2, 2, () -> "new", callbacks::incrementAndGet);
        f.complete.runOne();
        check(f.index.peek() == null, "Invalidated completion cannot publish");
        check(callbacks.get() == 0, "Invalidated completion cannot notify viewers");
        f.drain();
        check("new".equals(f.index.ready(source, 2, 2)), "Newest request publishes");
        check(f.index.ready(source, 2, 1) == null, "Index and stamp are read together");
        check(callbacks.get() == 1, "Only accepted result notifies viewers");

        f.index.get(source, 2, 3, () -> "unwanted", callbacks::incrementAndGet);
        f.prepare.runOne();
        f.worker.runOne();
        check("new".equals(f.index.get(source, 2, 2, () -> "unused", null)), "Cached previous request can return");
        f.complete.runOne();
        check("new".equals(f.index.peek()), "A to B to cached A must reject B completion");

        f.index.get(source, 2, 3, () -> "unwanted", null);
        f.prepare.runOne();
        f.worker.runOne();
        check("new".equals(f.index.ready(source, 2, 2)), "Fast readiness path recognizes cached A");
        f.complete.runOne();
        check("new".equals(f.index.peek()), "Readiness fast path cancels obsolete B too");

        f.index.invalidate();
        f.index.get(source, 2, 4, () -> "discarded", null);
        f.prepare.runOne();
        f.index.get(source, 2, 5, () -> "middle", null);
        f.index.get(source, 2, 6, () -> "latest", null);
        f.drain();
        check("latest".equals(f.index.ready(source, 2, 6)), "Pending generations coalesce to latest");
        check(f.errors.isEmpty(), "Stale requests must not report errors");
    }

    private static void rejectionAndRetry() {
        Fixture f = new Fixture();
        Object source = new Object();
        f.index.get(source, 1, 1, () -> { throw new IllegalStateException("temporary"); }, null);
        f.drain();
        check(f.errors.size() == 1 && f.index.peek() == null, "Failure leaves fallback available");
        f.index.get(source, 1, 1, () -> "retry", null);
        check(f.prepare.size() == 0, "Failed key respects retry cooldown");
        f.now.set(100);
        f.index.get(source, 1, 1, () -> "retry", null);
        f.drain();
        check("retry".equals(f.index.peek()), "Failure is not permanent");
        f.index.invalidate();
        f.prepare.reject = true;
        f.index.get(source, 1, 2, () -> "no", null);
        check(f.errors.size() == 2, "Preparation rejection is reported");
        f.prepare.reject = false;
        f.index.get(source, 1, 3, () -> "yes", null);
        f.drain();
        check("yes".equals(f.index.peek()), "Different key immediately retries rejection");
        f.worker.reject = true;
        f.index.get(source, 1, 4, () -> "no", null);
        f.drain();
        check(f.errors.size() == 3, "Worker rejection releases active request");
        f.worker.reject = false;
        f.complete.reject = true;
        f.index.get(source, 1, 5, () -> "no", null);
        f.drain();
        check(f.errors.size() == 4, "Completion rejection releases active request");
        f.complete.reject = false;
        f.index.invalidate();
        f.index.get(source, 1, 5, () -> "recovered", () -> { throw new IllegalArgumentException("callback"); });
        f.drain();
        check("recovered".equals(f.index.peek()), "Callback error cannot discard valid index");
        check(f.errors.size() == 5, "Callback errors are observable");
    }

    private static void sourceKeys() {
        Fixture f = new Fixture();
        String first = new String("same");
        String second = new String("same");
        f.index.get(first, 4, 10, () -> "first", null);
        f.drain();
        check(f.index.ready(second, 4, 10) == null, "Same-size replacement source must not reuse index");
        check(f.index.ready(first, 5, 10) == null, "Changed size must not reuse index");
        check(f.index.ready(first, 4, 11) == null, "Changed revision must not reuse index");
        f.index.get(second, 4, 10, () -> "second", null);
        f.drain();
        check("second".equals(f.index.peek()), "Source identity is part of published snapshot");
        f.index.invalidate();
        check(f.index.peek() == null, "Invalidate releases published index");
    }

    private static void batchCapture() {
        ManualExecutor prepare = new ManualExecutor();
        ManualExecutor worker = new ManualExecutor();
        ManualExecutor complete = new ManualExecutor();
        ManualExecutor continuation = new ManualExecutor();
        AtomicInteger captured = new AtomicInteger();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            values.add(i);
        }
        StagedSupplier<SearchIndex<Integer>> capture = EntrySnapshot.capture(values, value -> {
            check(prepare.running, "Game metadata is captured on prepare executor");
            captured.incrementAndGet();
            return new EntrySnapshot<>(value).modId("test").family("stone")
                    .add("Pedra " + value, SearchField.SOURCE_NATIVE);
        });
        AsyncIndexState<SearchIndex<Integer>> state = new AsyncIndexState<>(prepare, worker, complete,
                error -> { throw new AssertionError(error); }, continuation, () -> 0L, 100);
        state.getPrepared(values, values.size(), 0, () -> capture, null);
        prepare.runOne();
        check(captured.get() > 0 && captured.get() <= 256, "Capture enforces per-batch entry limit");
        check(worker.size() == 0, "Worker cannot start before all metadata is captured");
        while (continuation.size() > 0 || prepare.size() > 0) {
            continuation.runOne();
            prepare.runOne();
        }
        check(captured.get() == values.size(), "Every source is captured once");
        check(worker.size() == 1, "Exactly one index build follows all batches");
        worker.runOne();
        check(state.peek() == null, "Worker result waits for completion executor");
        complete.runOne();
        SearchSettings settings = new SearchSettings();
        check(state.peek().search(SearchQuery.parse("pedra", settings), settings).size() == values.size(),
                "Batched snapshot produces complete searchable index");

        captured.set(0);
        state.invalidate();
        state.getPrepared(values, values.size(), 1, () -> EntrySnapshot.capture(values, value -> {
            captured.incrementAndGet();
            return new EntrySnapshot<>(value).add("old", SearchField.SOURCE_NATIVE);
        }), null);
        prepare.runOne();
        int before = captured.get();
        state.invalidate();
        continuation.runOne();
        prepare.runOne();
        check(captured.get() == before && worker.size() == 0, "Invalidation cancels remaining metadata batches");
    }

    private static void registrySnapshots() {
        SourceSnapshot<Object> state = new SourceSnapshot<>();
        Object a = new Object();
        Object b = new Object();
        List<Object> registry = new ArrayList<>(Arrays.asList(a, b));
        List<Object> first = state.capture(Collections.unmodifiableList(registry));
        check(first == state.capture(Collections.unmodifiableList(registry)),
                "Fresh API wrappers around unchanged contents reuse snapshot identity");
        registry.set(1, new Object());
        List<Object> changed = state.capture(registry);
        check(changed != first && changed.size() == first.size(),
                "Same-cardinality registry replacement creates a new source key");
        check(first.get(1) == b, "A capture cannot mutate the previous build source");
        Collections.swap(registry, 0, 1);
        check(state.capture(registry) != changed, "Changed entry order rebuilds stable display ranking");
        boolean immutable = false;
        try {
            first.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "Published source snapshots are immutable");
        state.clear();
        check(state.capture(registry) != changed, "Invalidation releases source snapshot cache");
        Object owner = new Object();
        List<Object> owned = state.capture(owner, registry);
        check(state.capture(owner, registry) == owned, "Unchanged owner reuses source snapshot");
        check(state.capture(new Object(), registry) != owned, "Replaced viewer runtime changes source identity");
        SearchIndex<Object> blank = EntrySnapshot.index(Collections.singletonList(
                new EntrySnapshot<>(a).add("   ", SearchField.SOURCE_NATIVE)));
        check(blank.size() == 0, "Normalization cannot introduce empty indexed entries");
    }

    private static void concurrentRequests() {
        Fixture f = new Fixture();
        Object source = new Object();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < 12; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 1000; n++) {
                        f.index.get(source, 100, 1, () -> "parallel", null);
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            try {
                thread.join(5000);
                check(!thread.isAlive(), "Concurrent callers must not deadlock");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError(error);
            }
        }
        check(failures.get() == 0 && f.prepare.size() == 1,
                "Twelve threads and 12000 requests reserve a single build");
        f.drain();
        check("parallel".equals(f.index.peek()), "Concurrent reservation publishes the intended result");
    }

    private static void fatalFailures() {
        Fixture f = new Fixture();
        Object source = new Object();
        f.index.get(source, 1, 1, () -> { throw new AssertionError("fatal"); }, null);
        f.prepare.runOne();
        boolean propagated = false;
        try {
            f.worker.runOne();
        } catch (AssertionError expected) {
            propagated = true;
        }
        check(propagated && f.errors.isEmpty(), "Fatal Errors must propagate rather than become fallback");
        f.index.get(source, 1, 2, () -> "after", null);
        f.drain();
        check("after".equals(f.index.peek()), "Fatal cleanup releases lifecycle bookkeeping");
        f.index.get(source, 1, 3, () -> { throw new CompletionException(new AssertionError("wrapped fatal")); }, null);
        f.prepare.runOne();
        propagated = false;
        try {
            f.worker.runOne();
        } catch (AssertionError expected) {
            propagated = true;
        }
        check(propagated && f.errors.isEmpty(), "CompletionException must not hide fatal errors");
        f.index.get(source, 1, 4, () -> "final", null);
        f.drain();
        check("final".equals(f.index.peek()), "Wrapped fatal cleanup must release active request");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {
        final ManualExecutor prepare = new ManualExecutor();
        final ManualExecutor worker = new ManualExecutor();
        final ManualExecutor complete = new ManualExecutor();
        final AtomicLong now = new AtomicLong();
        final List<Throwable> errors = new ArrayList<>();
        final AsyncIndexState<String> index = new AsyncIndexState<>(prepare, worker, complete,
                errors::add, now::get, 100);

        void drain() {
            int rounds = 0;
            while (prepare.size() + worker.size() + complete.size() > 0) {
                if (++rounds > 100) {
                    throw new AssertionError("Non-terminating request queue");
                }
                prepare.runOne();
                worker.runOne();
                complete.runOne();
            }
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        boolean reject;
        boolean running;

        @Override
        public void execute(Runnable command) {
            if (reject) {
                throw new RejectedExecutionException("test");
            }
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runOne() {
            Runnable task = tasks.poll();
            if (task != null) {
                running = true;
                try {
                    task.run();
                } finally {
                    running = false;
                }
            }
        }
    }
}
