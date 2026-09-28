package com.rivalzin.bettersearch.async;

import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class StorageSearchSession<T> {
    private final AsyncIndexState<SearchIndex<T>> state;
    private final Consumer<Throwable> errors;
    private volatile Pass<T> current;
    private Object source;
    private int size = -1;
    private long inventoryRevision;
    private long languageRevision;
    private long epoch;
    private SearchSettings indexSettings;
    private Supplier<Supplier<SearchIndex<T>>> prepare;
    private Runnable onReady;
    private boolean closed;

    public StorageSearchSession(Executor client, Executor worker, Consumer<Throwable> errors) {
        this(client, worker, client, errors);
    }

    public StorageSearchSession(Executor prepareExecutor, Executor workerExecutor,
                                Executor completionExecutor, Consumer<Throwable> errors) {
        this.errors = Objects.requireNonNull(errors, "errors");
        state = new AsyncIndexState<>(prepareExecutor, workerExecutor, completionExecutor,
                this::report);
    }

    public synchronized void begin(Object sourceIdentity, int sourceSize, long inventoryRevision,
                                   long languageRevision, String fullQuery, SearchSettings settings,
                                   Supplier<Supplier<SearchIndex<T>>> prepare, Runnable onReady) {
        if (closed) {
            return;
        }
        Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(prepare, "prepare");
        if (sourceSize < 0) {
            throw new IllegalArgumentException("Negative source size");
        }
        SearchSettings captured = settings.copy();
        captured.maxResults = 0;
        captured.sortByRelevance = false;
        String query = fullQuery == null ? "" : fullQuery;
        boolean changedIndex = source != sourceIdentity || size != sourceSize
                || this.inventoryRevision != inventoryRevision
                || this.languageRevision != languageRevision || indexSettings == null
                || captured.affectsIndex(indexSettings) || captured.enabled != indexSettings.enabled;
        if (changedIndex) {
            state.invalidate();
            epoch++;
        }
        source = sourceIdentity;
        size = sourceSize;
        this.inventoryRevision = inventoryRevision;
        this.languageRevision = languageRevision;
        indexSettings = captured;
        this.prepare = prepare;
        this.onReady = onReady;
        Pass<T> pass = current;
        if (pass == null || !query.equals(pass.query)) {
            current = new Pass<>(query, captured);
            return;
        }
        boolean changedSettings = !captured.equals(pass.settings);
        pass.settings = captured;
        if (changedIndex || changedSettings) {
            if (changedIndex) {
                pass.index = null;
            }
            for (Term<T> term : pass.terms.values()) {
                term.matches = null;
            }
        }
        if (!captured.enabled || size == 0 || pass.query.trim().isEmpty() || pass.terms.isEmpty()) {
            return;
        }
        if (pass.index == null) {
            SearchIndex<T> ready = state.getPrepared(source, size, epoch, prepare, this::published);
            if (ready != null && current == pass) {
                update(pass, ready);
            }
        } else if (changedSettings) {
            for (Term<T> term : pass.terms.values()) {
                search(pass, term);
            }
        }
    }

    public synchronized Predicate<T> matcher(String plainTerm) {
        Pass<T> pass = current;
        if (closed || pass == null || !pass.settings.enabled || size == 0
                || pass.query.trim().isEmpty() || !isPlainTerm(plainTerm)) {
            return value -> false;
        }
        Term<T> term = pass.terms.get(plainTerm);
        if (term == null) {
            term = new Term<>(plainTerm);
            pass.terms.put(plainTerm, term);
        }
        if (pass.index == null) {
            SearchIndex<T> ready = state.getPrepared(source, size, epoch, prepare, this::published);
            if (ready != null && current == pass) {
                update(pass, ready);
            }
        } else if (term.matches == null) {
            search(pass, term);
        }
        Term<T> captured = term;
        return value -> current == pass && captured.matches != null && captured.matches.contains(value);
    }

    public synchronized void invalidate() {
        state.invalidate();
        epoch++;
        current = null;
        source = null;
        size = -1;
        indexSettings = null;
        prepare = null;
        onReady = null;
    }

    public synchronized void close() {
        if (!closed) {
            closed = true;
            invalidate();
        }
    }

    private void published() {
        Runnable refresh;
        synchronized (this) {
            Pass<T> pass = current;
            if (closed || pass == null || !pass.settings.enabled || pass.terms.isEmpty()) {
                return;
            }
            SearchIndex<T> ready = state.ready(source, size, epoch);
            if (ready == null || pass.index == ready) {
                return;
            }
            update(pass, ready);
            refresh = onReady;
        }
        if (refresh != null) {
            refresh.run();
        }
    }

    private void update(Pass<T> pass, SearchIndex<T> ready) {
        if (pass.index == ready) {
            return;
        }
        pass.index = ready;
        for (Term<T> term : pass.terms.values()) {
            search(pass, term);
        }
    }

    private void search(Pass<T> pass, Term<T> term) {
        try {
            SearchQuery query = SearchQuery.parse(term.text, pass.settings);
            Set<T> matches = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
            if (!query.isEmpty()) {
                matches.addAll(pass.index.search(query, pass.settings));
            }
            term.matches = Collections.unmodifiableSet(matches);
        } catch (RuntimeException error) {
            term.matches = Collections.emptySet();
            report(error);
        }
    }

    private void report(Throwable error) {
        FailurePolicy.rethrowFatal(error);
        errors.accept(error);
    }

    public static boolean isPlainTerm(String term) {
        if (term == null || term.trim().isEmpty()) {
            return false;
        }
        boolean text = false;
        for (int offset = 0; offset < term.length();) {
            int point = term.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isLetterOrDigit(point)) {
                text = true;
            } else if (!Character.isWhitespace(point) && point != '_' && point != '-'
                    && Character.getType(point) != Character.NON_SPACING_MARK
                    && Character.getType(point) != Character.COMBINING_SPACING_MARK) {
                return false;
            }
        }
        return text;
    }

    private static final class Pass<T> {
        final String query;
        SearchSettings settings;
        final Map<String, Term<T>> terms = new HashMap<>();
        SearchIndex<T> index;

        Pass(String query, SearchSettings settings) {
            this.query = query;
            this.settings = settings;
        }
    }

    private static final class Term<T> {
        final String text;
        volatile Set<T> matches;

        Term(String text) {
            this.text = text;
        }
    }
}
