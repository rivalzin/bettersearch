package com.rivalzin.bettersearch.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.state.VersionedState;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class BetterSearchStateTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        verifyRevisionOrdering();
        verifyResourceReloadOrdering();
        verifyConcurrentInvalidation();
        verifyConfig(Paths.get(args[0]));
        System.out.println("BetterSearchStateTest: " + checks + " checks passed");
    }

    private static void verifyRevisionOrdering() {
        VersionedState<String> state = new VersionedState<>("A");
        long toB = state.begin();
        state.invalidate();
        check(!state.publish(toB, "B"), "Returning to loaded A must discard unfinished B");
        check("A".equals(state.value()), "Invalidated load must preserve loaded A");
        long firstA = state.begin();
        long laterA = state.begin();
        check(!state.publish(firstA, "obsolete A"), "Equal requested language must still compare revisions");
        check(state.publish(laterA, "new A"), "Newest load must publish");
        check(!state.publish(laterA, "duplicate"), "Completion must publish at most once");
        long failed = state.begin();
        check(state.fail(failed), "Current failure must release pending state");
        check(!state.pending(), "Failed load must permit retry");
        long retry = state.begin();
        check(!state.fail(failed), "Stale failure must not cancel newer work");
        check(state.pending(), "Retry must remain pending");
        check(state.publish(retry, "recovered"), "Retry must recover");
    }

    private static void verifyConcurrentInvalidation() throws Exception {
        VersionedState<Integer> state = new VersionedState<>(-1);
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        for (int index = 0; index < 32; index++) {
            final long revision = state.begin();
            final int value = index;
            Thread worker = new Thread(() -> {
                try {
                    release.await();
                    if (!state.publish(revision, value)) {
                        rejected.incrementAndGet();
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            workers.add(worker);
            worker.start();
        }
        state.invalidate();
        long current = state.begin();
        release.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        check(failure.get() == null, "Concurrent completions must terminate normally");
        check(rejected.get() == 32, "All completions invalidated before publication must be rejected");
        check(state.publish(current, 100), "Current completion must survive stale completions");
        check(state.value() == 100, "Only current state must remain visible");
    }

    private static void verifyResourceReloadOrdering() {
        VersionedState<String> state = new VersionedState<>("old resource A");
        state.reset(null);
        long resourceReload = state.begin();
        state.invalidate();
        state.invalidate();
        check(!state.publish(resourceReload, "new resource A"), "Settings ABA must invalidate the prepared resource load");
        check(state.value() == null, "An invalidated resource reload must not expose the old matching language table");
        long latest = state.begin();
        check(state.publish(latest, "new resource A"), "Current language must reload after the resource barrier opens");
        check("new resource A".equals(state.value()), "Only the new resource translation must remain visible");
    }

    private static void verifyConfig(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path missing = directory.resolve("missing.json");
        SearchSettings defaults = ConfigIo.loadOrCreate(missing);
        check(Files.isRegularFile(missing), "Missing configuration must be created");
        check(defaults.enabled, "Missing configuration must load defaults");

        Path invalidField = directory.resolve("invalid-field.json");
        Files.write(invalidField, "{\"enabled\":false,\"typoTolerance\":{},\"maxResults\":27,\"languages\":[\" PT_BR \",null,\"pt_br\"]}".getBytes(StandardCharsets.UTF_8));
        SearchSettings recovered = ConfigIo.loadOrCreate(invalidField);
        check(!recovered.enabled, "A malformed field must preserve valid boolean settings");
        check(recovered.maxResults == 27, "A malformed field must preserve later settings");
        check(recovered.typoTolerance == 2, "A malformed field must retain its default");
        check(recovered.languages.size() == 1 && "pt_br".equals(recovered.languages.get(0)), "Language list must sanitize and deduplicate");

        Path malformed = directory.resolve("malformed.json");
        Files.write(malformed, "{ broken".getBytes(StandardCharsets.UTF_8));
        check(ConfigIo.loadOrCreate(malformed).enabled, "Malformed document must recover defaults");
        check(parse(malformed).has("enabled"), "Malformed document must be repaired as valid JSON");

        Path readFailure = directory.resolve("directory.json");
        Files.createDirectories(readFailure);
        ConfigIo.loadOrCreate(readFailure);
        check(Files.isDirectory(readFailure), "I/O failure must leave the existing object untouched");

        Path relative = Paths.get("build/state-relative.json");
        ConfigIo.save(relative, recovered);
        check(parse(relative).get("maxResults").getAsInt() == 27, "Relative config paths must save");

        Path competing = directory.resolve("concurrent.json");
        List<Thread> writers = new ArrayList<>();
        AtomicInteger saved = new AtomicInteger();
        for (int count = 1; count <= 24; count++) {
            final int resultLimit = count;
            Thread writer = new Thread(() -> {
                SearchSettings own = new SearchSettings();
                own.maxResults = resultLimit;
                if (ConfigIo.save(competing, own)) {
                    saved.incrementAndGet();
                }
            });
            writers.add(writer);
            writer.start();
        }
        for (Thread writer : writers) {
            writer.join();
        }
        JsonObject out = parse(competing);
        int result = out.get("maxResults").getAsInt();
        check(result >= 1 && result <= 24, "Concurrent saves must publish one complete writer");
        check(saved.get() == 24, "Every concurrent save must complete successfully");
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            check(!files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")), "Completed saves must remove temporary files");
        }
        SearchSettings invalid = new SearchSettings();
        invalid.typoTolerance = 999;
        invalid.languages = null;
        ConfigIo.save(directory.resolve("sanitize-copy.json"), invalid);
        check(invalid.typoTolerance == 999 && invalid.languages == null, "Save must not mutate caller settings");
    }

    private static JsonObject parse(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
