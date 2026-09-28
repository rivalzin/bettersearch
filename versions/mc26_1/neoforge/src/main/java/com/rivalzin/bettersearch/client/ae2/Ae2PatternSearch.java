package com.rivalzin.bettersearch.client.ae2;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.CreativeIndexBuilder;
import com.rivalzin.bettersearch.client.LanguageTable;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Ae2PatternSearch {
    private final Screen owner;
    private final Runnable refresh;
    private final Function<ItemStack, List<Object>> outputs;
    private final StorageInventory<RecordKey> inventory = new StorageInventory<>();
    private final StorageSearchSession<RecordKey> session;
    private SearchSettings previousSettings;
    private long previousLanguage = Long.MIN_VALUE;
    private Predicate<RecordKey> matcher;
    private boolean dirty = true;
    private boolean closed;

    public Ae2PatternSearch(Screen owner, Runnable refresh, Function<ItemStack, List<Object>> outputs) {
        this.owner = owner;
        this.refresh = refresh;
        this.outputs = outputs;
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task), error -> {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.error("[{}] failed to build AE2 pattern search index",
                            BetterSearch.MOD_NAME, error);
                });
    }

    public void dirty() {
        dirty = true;
    }

    public boolean needsRefresh() {
        return !closed && (previousSettings == null || !previousSettings.equals(Ae2StorageSearch.settings())
                || previousLanguage != BetterSearchClient.languageStamp());
    }

    public boolean isEnabled() {
        return !closed && previousSettings != null && previousSettings.enabled;
    }

    public void update(Collection<?> records, String query) {
        if (closed) {
            return;
        }
        SearchSettings settings = Ae2StorageSearch.settings();
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        previousSettings = settings;
        previousLanguage = BetterSearchClient.languageStamp();
        if (settings.enabled && dirty && StorageSearchSession.isPlainTerm(query)) {
            inventory.update(new ArrayList<>(records), value -> value instanceof Ae2PatternRecordAccess
                    ? new RecordKey((Ae2PatternRecordAccess) value, false) : null, RecordKey::copy);
            dirty = false;
        }
        List<RecordKey> values = inventory.values();
        session.begin(values, values.size(), 0, previousLanguage, query, settings,
                () -> prepare(values, settings), () -> {
                    if (!closed && Minecraft.getInstance().screen == owner
                            && Minecraft.getInstance().player != null) {
                        refresh.run();
                    }
                });
        matcher = session.matcher(query);
    }

    public boolean matches(Object record) {
        return !closed && matcher != null && matcher.test(inventory.key(record));
    }

    public void close() {
        closed = true;
        session.close();
        inventory.clear();
        matcher = null;
        previousSettings = null;
    }

    private Supplier<SearchIndex<RecordKey>> prepare(List<RecordKey> values, SearchSettings settings) {
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        return EntrySnapshot.capture(values, value -> {
            if (closed || Minecraft.getInstance().player == null) {
                return null;
            }
            EntrySnapshot<RecordKey> entry = new EntrySnapshot<>(value);
            entry.add(value.name, SearchField.SOURCE_NATIVE);
            for (ItemStack stack : value.stacks) {
                try {
                    for (Object output : outputs.apply(stack)) {
                        Ae2IndexText.fill(entry, output, languages, codes, settings);
                    }
                } catch (RuntimeException | LinkageError error) {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] skipped AE2 pattern: {}", BetterSearch.MOD_NAME, error.toString());
                }
            }
            return entry;
        }, false);
    }

    private static final class RecordKey {
        private final long serverId;
        private final String name;
        private final List<ItemStack> stacks;
        private final int hash;

        private RecordKey(Ae2PatternRecordAccess record, boolean copy) {
            serverId = record.getServerId();
            name = record.getSearchName();
            stacks = new ArrayList<>();
            for (Object value : record.bettersearch$inventory()) {
                if (value instanceof ItemStack && !((ItemStack) value).isEmpty()) {
                    stacks.add(copy ? ((ItemStack) value).copy() : (ItemStack) value);
                }
            }
            hash = hash();
        }

        private RecordKey(RecordKey source) {
            serverId = source.serverId;
            name = source.name;
            stacks = new ArrayList<>(source.stacks.size());
            for (ItemStack stack : source.stacks) {
                stacks.add(stack.copy());
            }
            hash = hash();
        }

        private RecordKey copy() {
            return new RecordKey(this);
        }

        private int hash() {
            int result = 31 * Long.hashCode(serverId) + Objects.hashCode(name);
            for (ItemStack stack : stacks) {
                result = 31 * result + ItemStack.hashItemAndComponents(stack);
            }
            return result;
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
            if (!(other instanceof RecordKey)) {
                return false;
            }
            RecordKey key = (RecordKey) other;
            if (serverId != key.serverId || !Objects.equals(name, key.name) || stacks.size() != key.stacks.size()) {
                return false;
            }
            for (int i = 0; i < stacks.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(stacks.get(i), key.stacks.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
