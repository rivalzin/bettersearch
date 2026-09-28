package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Ae2Search {
    private final Screen owner;
    private final StorageSearchSession<Object> session;
    private final StorageInventory<Object> inventory = new StorageInventory<>();
    private final Map<String, Predicate<Object>> terms = new HashMap<>();
    private SearchSettings previousSettings;
    private long previousLanguage = Long.MIN_VALUE;
    private String previousQuery;
    private boolean dirty = true;
    private boolean ready;
    private boolean closed;

    public Ae2Search(Screen owner) {
        this.owner = owner;
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task),
                error -> report("index", error));
    }

    public void changed() {
        dirty = true;
    }

    public boolean needsRefresh() {
        return !closed && (ready || changedSettings());
    }

    public boolean update(Collection<?> source, String query) {
        if (closed) {
            return false;
        }
        SearchSettings settings = settings();
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long language = BetterSearchClient.languageStamp();
        boolean changed = previousSettings == null || !settings.equals(previousSettings)
                || previousLanguage != language || ready;
        ready = false;
        List<Object> previous = inventory.values();
        if (settings.enabled && dirty && hasPlainText(query)) {
            inventory.update(new ArrayList<>(source), value -> value instanceof Ae2EntryAccess
                    ? ((Ae2EntryAccess) value).bettersearch$key() : null, value -> value);
            dirty = false;
        }
        List<Object> values = inventory.values();
        changed |= values != previous;
        if (changed || !query.equals(previousQuery)) {
            terms.clear();
        }
        previousSettings = settings;
        previousLanguage = language;
        previousQuery = query;
        session.begin(values, values.size(), 0, language, query, settings,
                () -> prepare(values, settings), () -> {
                    if (active()) {
                        ready = true;
                    }
                });
        return changed;
    }

    public boolean matches(String term, Object entry) {
        if (closed || !StorageSearchSession.isPlainTerm(term)) {
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
        previousQuery = null;
        ready = false;
    }

    private boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return !closed && minecraft.screen == owner && minecraft.player != null;
    }

    private boolean changedSettings() {
        return previousSettings == null || !settings().equals(previousSettings)
                || previousLanguage != BetterSearchClient.languageStamp();
    }

    public static SearchSettings settings() {
        SearchSettings settings = BetterSearchClient.settings();
        settings.enabled &= settings.searchAe2;
        return settings;
    }

    private Supplier<SearchIndex<Object>> prepare(List<Object> keys, SearchSettings settings) {
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        return EntrySnapshot.capture(keys, key -> {
            try {
                EntrySnapshot<Object> entry = new EntrySnapshot<>(key);
                fill(entry, key, languages, codes, settings);
                return entry;
            } catch (RuntimeException | LinkageError error) {
                report("resource", error);
                return null;
            }
        }, false);
    }

    public static void fill(EntrySnapshot<?> entry, Object key, LanguageTable languages,
                            List<String> codes, SearchSettings settings) {
        if (!(key instanceof Ae2KeyAccess)) {
            return;
        }
        Ae2KeyAccess resource = (Ae2KeyAccess) key;
        ResourceLocation id = resource.getId();
        entry.modId(id.getNamespace());
        entry.family(id.getPath());
        if (key instanceof Ae2ItemAccess) {
            CreativeIndexBuilder.fill(entry, ((Ae2ItemAccess) key).toStack(), languages,
                    codes, settings, Minecraft.getInstance().player,
                    CreativeIndexBuilder.englishSearched(codes));
        }
        fillName(entry, resource.getDisplayName(), languages, codes, settings);
        if (settings.searchItemIds) {
            entry.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '), SearchField.SOURCE_ID);
        }
    }

    public static void fillName(EntrySnapshot<?> entry, Component name, LanguageTable languages,
                                List<String> codes, SearchSettings settings) {
        if (name == null) {
            return;
        }
        entry.add(name.getString(), SearchField.SOURCE_NATIVE);
        if (settings.crossLanguage) {
            for (String code : codes) {
                entry.add(translate(name, code, languages, 0), code.equals("en_us")
                        ? SearchField.SOURCE_ENGLISH : SearchField.SOURCE_FOREIGN);
            }
        }
    }

    private static String translate(Component name, String code, LanguageTable languages, int depth) {
        if (depth > 32) {
            return name.getString();
        }
        String text = name.plainCopy().getString();
        if (name instanceof TranslatableComponent contents) {
            String translated = languages.get(code, contents.getKey());
            if (translated != null) {
                Object[] args = contents.getArgs().clone();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Component component) {
                        args[i] = translate(component, code, languages, depth + 1);
                    }
                }
                try {
                    text = String.format(Locale.ROOT, translated, args);
                } catch (java.util.IllegalFormatException error) {
                    text = translated;
                }
            }
        }
        StringBuilder result = new StringBuilder(text);
        for (Component sibling : name.getSiblings()) {
            result.append(translate(sibling, code, languages, depth + 1));
        }
        return result.toString();
    }

    private static boolean hasPlainText(String query) {
        return StorageSearchSession.isPlainTerm(query);
    }

    public static void report(String part, Throwable error) {
        FailurePolicy.rethrowFatal(error);
        BetterSearch.LOGGER.error("[{}] failed AE2 search {}", BetterSearch.MOD_NAME, part, error);
    }
}
