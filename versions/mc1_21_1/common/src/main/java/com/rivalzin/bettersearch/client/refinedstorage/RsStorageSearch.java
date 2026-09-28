package com.rivalzin.bettersearch.client.refinedstorage;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.CreativeIndexBuilder;
import com.rivalzin.bettersearch.client.LanguageTable;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class RsStorageSearch {
    private final Screen owner;
    private final Runnable refresh;
    private final StorageInventory<Object> inventory = new StorageInventory<>();
    private final StorageSearchSession<Object> session;
    private final Map<String, Predicate<Object>> terms = new HashMap<>();
    private final Map<Object, String> literals = new IdentityHashMap<>();
    private SearchSettings previousSettings;
    private long previousLanguage = Long.MIN_VALUE;
    private String previousQuery;
    private List<Object> previousValues;
    private boolean dirty = true;
    private boolean closed;

    public RsStorageSearch(Screen owner, Runnable refresh) {
        this.owner = owner;
        this.refresh = refresh;
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task), error -> {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.error("[{}] failed to build Refined Storage search index",
                            BetterSearch.MOD_NAME, error);
                });
    }

    public void dirty() {
        dirty = true;
    }

    public boolean needsRefresh() {
        return !closed && (dirty || previousSettings == null || !previousSettings.equals(settings())
                || previousLanguage != BetterSearchClient.languageStamp());
    }

    public void update(Object repository, String query) {
        if (closed) {
            return;
        }
        SearchSettings settings = settings();
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long language = BetterSearchClient.languageStamp();
        boolean changed = previousSettings == null || !previousSettings.equals(settings)
                || previousLanguage != language;
        dirty |= changed || !Objects.equals(previousQuery, query);
        previousSettings = settings;
        previousLanguage = language;
        if (dirty && settings.enabled && query != null && !query.isBlank()) {
            try {
                inventory.update(RsApi.entries(repository), value -> value, value -> value);
            } catch (RuntimeException | LinkageError error) {
                FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.error("[{}] incompatible Refined Storage repository",
                        BetterSearch.MOD_NAME, error);
                close();
                return;
            }
        }
        dirty = false;
        List<Object> values = inventory.values();
        changed |= previousValues != values;
        previousValues = values;
        if (changed || !Objects.equals(previousQuery, query)) {
            terms.clear();
            literals.clear();
            previousQuery = query;
        }
        session.begin(values, values.size(), 0, language, query, settings,
                () -> prepare(values, settings), () -> {
                    if (!closed && Minecraft.getInstance().screen == owner
                            && Minecraft.getInstance().player != null) {
                        refresh.run();
                    }
                });
    }

    public boolean matches(Object resource, Object literal) {
        if (closed) {
            return false;
        }
        String term = literals.computeIfAbsent(literal, RsApi::term);
        return terms.computeIfAbsent(term, session::matcher).test(inventory.key(resource));
    }

    public void close() {
        closed = true;
        session.close();
        inventory.clear();
        terms.clear();
        literals.clear();
        previousSettings = null;
        previousValues = null;
    }

    private static SearchSettings settings() {
        SearchSettings settings = BetterSearchClient.settings();
        settings.enabled &= settings.searchRefinedStorage;
        return settings;
    }

    private Supplier<SearchIndex<Object>> prepare(List<Object> values, SearchSettings settings) {
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        return EntrySnapshot.capture(values, value -> {
            if (closed || Minecraft.getInstance().player == null) {
                return null;
            }
            try {
                EntrySnapshot<Object> entry = new EntrySnapshot<>(value);
                RsIndexText.fill(entry, value, languages, codes, settings);
                return entry;
            } catch (RuntimeException | LinkageError error) {
                FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] skipped Refined Storage resource: {}",
                        BetterSearch.MOD_NAME, error.toString());
                return null;
            }
        }, false);
    }
}
