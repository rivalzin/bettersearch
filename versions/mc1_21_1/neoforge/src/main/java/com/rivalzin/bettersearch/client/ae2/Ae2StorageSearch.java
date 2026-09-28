package com.rivalzin.bettersearch.client.ae2;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Ae2StorageSearch {
    private final Screen owner;
    private final Runnable refresh;
    private final StorageInventory<Object> inventory = new StorageInventory<>();
    private final StorageSearchSession<Object> session;
    private final Map<String, Predicate<Object>> terms = new HashMap<>();
    private SearchSettings previousSettings;
    private long previousLanguage = Long.MIN_VALUE;
    private String previousQuery;
    private List<Object> previousValues;
    private boolean dirty = true;
    private boolean closed;

    public Ae2StorageSearch(Screen owner, Runnable refresh) {
        this.owner = owner;
        this.refresh = refresh;
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task), error -> {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.error("[{}] failed to build AE2 search index", BetterSearch.MOD_NAME, error);
                });
    }

    public void dirty() {
        dirty = true;
    }

    public boolean needsRefresh() {
        return !closed && (previousSettings == null || !previousSettings.equals(settings())
                || previousLanguage != BetterSearchClient.languageStamp());
    }

    public boolean update(Collection<?> entries, String query) {
        if (closed) {
            return false;
        }
        SearchSettings settings = settings();
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long language = BetterSearchClient.languageStamp();
        boolean changed = previousSettings == null || !previousSettings.equals(settings)
                || previousLanguage != language;
        previousSettings = settings;
        previousLanguage = language;
        if (settings.enabled && dirty && hasPlainText(query)) {
            inventory.update(new ArrayList<>(entries), value -> value instanceof Ae2EntryAccess
                    ? ((Ae2EntryAccess) value).bettersearch$key() : null, value -> value);
            dirty = false;
        }
        List<Object> values = inventory.values();
        changed |= previousValues != values;
        previousValues = values;
        if (changed || !java.util.Objects.equals(previousQuery, query)) {
            terms.clear();
            previousQuery = query;
        }
        session.begin(values, values.size(), 0, language, query, settings,
                () -> prepare(values, settings), () -> {
                    if (!closed && Minecraft.getInstance().screen == owner
                            && Minecraft.getInstance().player != null) {
                        refresh.run();
                    }
                });
        return changed;
    }

    public boolean matches(Object entry, String term) {
        if (closed) {
            return false;
        }
        return terms.computeIfAbsent(term, session::matcher).test(inventory.key(entry));
    }

    public void close() {
        closed = true;
        session.close();
        inventory.clear();
        terms.clear();
        previousSettings = null;
        previousValues = null;
    }

    public static SearchSettings settings() {
        SearchSettings settings = BetterSearchClient.settings();
        settings.enabled &= settings.searchAe2;
        return settings;
    }

    private static boolean hasPlainText(String query) {
        if (query == null) {
            return false;
        }
        for (String term : query.split("[|\\s]+")) {
            if (StorageSearchSession.isPlainTerm(term)) {
                return true;
            }
        }
        return false;
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
                Ae2IndexText.fill(entry, value, languages, codes, settings);
                return entry;
            } catch (RuntimeException | LinkageError error) {
                FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] skipped AE2 resource: {}", BetterSearch.MOD_NAME, error.toString());
                return null;
            }
        }, false);
    }
}
