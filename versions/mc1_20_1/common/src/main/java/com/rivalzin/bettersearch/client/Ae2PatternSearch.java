package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Ae2PatternSearch {
    private final Screen owner;
    private final StorageSearchSession<PatternKey> session;
    private final StorageInventory<PatternKey> inventory = new StorageInventory<>();
    private SearchSettings previousSettings;
    private long previousLanguage = Long.MIN_VALUE;
    private String previousQuery;
    private Predicate<PatternKey> matching;
    private boolean dirty = true;
    private boolean refresh;
    private boolean ready;
    private boolean closed;

    public Ae2PatternSearch(Screen owner) {
        this.owner = owner;
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task),
                error -> Ae2Search.report("pattern index", error));
    }

    public void changed() {
        dirty = true;
        refresh = true;
    }

    public boolean needsRefresh() {
        return !closed && (refresh || ready || previousSettings == null
                || !Ae2Search.settings().equals(previousSettings)
                || previousLanguage != BetterSearchClient.languageStamp());
    }

    public boolean update(Collection<?> records, String query) {
        refresh = false;
        SearchSettings settings = Ae2Search.settings();
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long language = BetterSearchClient.languageStamp();
        boolean changed = ready || previousSettings == null || !settings.equals(previousSettings)
                || previousLanguage != language;
        List<PatternKey> previous = inventory.values();
        if (dirty && settings.enabled && StorageSearchSession.isPlainTerm(query)) {
            inventory.update(new ArrayList<>(records), record -> new PatternKey((Ae2PatternRecordAccess) record),
                    PatternKey::snapshot);
            dirty = false;
        }
        changed |= previous != inventory.values();
        if (changed || !query.equals(previousQuery)) {
            matching = null;
        }
        ready = false;
        previousSettings = settings;
        previousLanguage = language;
        previousQuery = query;
        List<PatternKey> values = inventory.values();
        session.begin(values, values.size(), 0, language, query, settings,
                () -> prepare(values, settings), () -> {
                    if (!closed && Minecraft.getInstance().screen == owner) {
                        ready = true;
                    }
                });
        return changed || settings.enabled && StorageSearchSession.isPlainTerm(query);
    }

    public boolean matches(Object record) {
        if (closed || previousQuery == null) {
            return false;
        }
        if (matching == null) {
            matching = session.matcher(previousQuery);
        }
        return matching.test(inventory.key(record));
    }

    public void close() {
        closed = true;
        session.close();
        inventory.clear();
        previousSettings = null;
        previousQuery = null;
        matching = null;
        ready = false;
    }

    private Supplier<SearchIndex<PatternKey>> prepare(List<PatternKey> records, SearchSettings settings) {
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        return EntrySnapshot.capture(records, record -> {
            EntrySnapshot<PatternKey> entry = new EntrySnapshot<>(record);
            Ae2Search.fillName(entry, record.name, languages, codes, settings);
            for (ItemStack pattern : record.patterns) {
                if (!pattern.isEmpty()) {
                    try {
                        for (Object output : PatternDecoder.outputs(pattern)) {
                            if (output instanceof Ae2EntryAccess) {
                                Ae2Search.fill(entry, ((Ae2EntryAccess) output).bettersearch$key(),
                                        languages, codes, settings);
                            }
                        }
                    } catch (RuntimeException | LinkageError error) {
                        Ae2Search.report("pattern", error);
                    }
                }
            }
            return entry;
        }, false);
    }

    private static final class PatternKey {
        private final long id;
        private final Component name;
        private final List<ItemStack> patterns;
        private final int hash;

        private PatternKey(Ae2PatternRecordAccess record) {
            id = record.getServerId();
            name = record.bettersearch$name();
            patterns = new ArrayList<>();
            Iterable<ItemStack> contents = record.bettersearch$inventory();
            if (contents != null) {
                contents.forEach(patterns::add);
            }
            hash = hash(id, name, patterns);
        }

        private PatternKey(long id, Component name, List<ItemStack> patterns) {
            this.id = id;
            this.name = name;
            this.patterns = patterns;
            hash = hash(id, name, patterns);
        }

        private PatternKey snapshot() {
            List<ItemStack> copy = new ArrayList<>(patterns.size());
            for (ItemStack pattern : patterns) {
                copy.add(pattern.copy());
            }
            return new PatternKey(id, name == null ? null : name.copy(), copy);
        }

        private static int hash(long id, Component name, List<ItemStack> patterns) {
            int hash = 31 * Long.hashCode(id) + Objects.hashCode(name);
            for (ItemStack pattern : patterns) {
                hash = 31 * hash + System.identityHashCode(pattern.getItem());
                hash = 31 * hash + Objects.hashCode(pattern.getTag());
            }
            return hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PatternKey key) || id != key.id
                    || !Objects.equals(name, key.name) || patterns.size() != key.patterns.size()) {
                return false;
            }
            for (int i = 0; i < patterns.size(); i++) {
                if (!ItemStack.isSameItemSameTags(patterns.get(i), key.patterns.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class PatternDecoder {
        private static final Method DECODE;
        private static final Method OUTPUTS;

        static {
            try {
                ClassLoader loader = Ae2PatternSearch.class.getClassLoader();
                DECODE = Class.forName("appeng.api.crafting.PatternDetailsHelper", false, loader)
                        .getMethod("decodePattern", ItemStack.class, Level.class, boolean.class);
                OUTPUTS = Class.forName("appeng.api.crafting.IPatternDetails", false, loader)
                        .getMethod("getOutputs");
            } catch (ReflectiveOperationException error) {
                throw new ExceptionInInitializerError(error);
            }
        }

        private static Object[] outputs(ItemStack stack) {
            try {
                Object pattern = DECODE.invoke(null, stack, Minecraft.getInstance().level, false);
                return pattern == null ? new Object[0] : (Object[]) OUTPUTS.invoke(pattern);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(cause);
                if (cause instanceof RuntimeException exception) {
                    throw exception;
                }
                if (cause instanceof LinkageError linkage) {
                    throw linkage;
                }
                throw new IllegalStateException(cause);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }
}
