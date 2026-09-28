package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StorageInventory;
import com.rivalzin.bettersearch.async.StorageSearchSession;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.mixin.toms.TomsStorageStackAccessor;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TomsStorageSearch {
    private final Screen owner;
    private final Runnable requestRefresh;
    private final StorageSearchSession<StackKey> session;
    private final StorageInventory<StackKey> inventory = new StorageInventory<>();
    private SearchSettings previousSettings;
    private long previousLanguageRevision = Long.MIN_VALUE;
    private Object previousItems;
    private int previousSize = -1;
    private boolean pendingInventory = true;
    private boolean closed;
    private Predicate<StackKey> legacyMatcher;

    public TomsStorageSearch(Screen owner, Runnable requestRefresh) {
        this.owner = Objects.requireNonNull(owner);
        this.requestRefresh = Objects.requireNonNull(requestRefresh);
        session = new StorageSearchSession<>(task -> Minecraft.getInstance().execute(task),
                task -> Util.backgroundExecutor().execute(task),
                error -> {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.error("[{}] failed to build Tom's Storage search index",
                            BetterSearch.MOD_NAME, error);
                });
    }

    public boolean update(List<?> items, boolean inventoryChanged, String rawQuery) {
        if (closed || items == null) {
            return false;
        }
        SearchSettings settings = BetterSearchClient.settings();
        settings.enabled = settings.enabled && settings.searchTomsStorage;
        if (settings.enabled && settings.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long languageRevision = BetterSearchClient.languageStamp();
        boolean changed = previousSettings == null || !previousSettings.equals(settings)
                || previousLanguageRevision != languageRevision;
        previousSettings = settings;
        previousLanguageRevision = languageRevision;
        pendingInventory |= inventoryChanged || previousItems != items || previousSize != items.size();
        previousItems = items;
        previousSize = items.size();
        if (settings.enabled && pendingInventory && hasPlainText(rawQuery)) {
            inventory.update(items, this::key, value -> new StackKey(value.stack.copy()));
            pendingInventory = false;
        }
        legacyMatcher = null;
        List<StackKey> values = inventory.values();
        session.begin(values, values.size(), 0, languageRevision,
                rawQuery, settings, () -> prepare(values, settings), () -> {
                    if (!closed && isActive()) {
                        requestRefresh.run();
                    }
                });
        return changed;
    }

    public Predicate<Object> extend(String term, Predicate<Object> nativePredicate) {
        if (closed) {
            return nativePredicate;
        }
        Predicate<StackKey> matching = session.matcher(term);
        return nativePredicate.or(item -> matching.test(inventory.key(item)));
    }

    public boolean matches(Object item, String query) {
        if (closed) {
            return false;
        }
        if (legacyMatcher == null) {
            legacyMatcher = session.matcher(query);
        }
        return legacyMatcher.test(inventory.key(item));
    }

    public void close() {
        closed = true;
        session.close();
        inventory.clear();
        previousSettings = null;
        previousItems = null;
        legacyMatcher = null;
    }

    private boolean isActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gui.screen() == owner && minecraft.player != null;
    }

    private static boolean hasPlainText(String query) {
        if (query == null) {
            return false;
        }
        for (String term : query.split("[| ]")) {
            if (StorageSearchSession.isPlainTerm(term)) {
                return true;
            }
        }
        return false;
    }

    private StackKey key(Object item) {
        if (!(item instanceof TomsStorageStackAccessor)) {
            return null;
        }
        try {
            ItemStack stack = ((TomsStorageStackAccessor) item).bettersearch$getStack();
            return stack == null || stack.isEmpty() ? null : new StackKey(stack);
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.debug("[{}] skipped stored item: {}",
                    BetterSearch.MOD_NAME, error.toString());
            return null;
        }
    }

    private Supplier<SearchIndex<StackKey>> prepare(List<StackKey> items, SearchSettings settings) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || closed) {
            return () -> new SearchIndex<>(java.util.Collections.emptyList(), false);
        }
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        return EntrySnapshot.capture(items, item -> {
            try {
                EntrySnapshot<StackKey> entry = new EntrySnapshot<>(item);
                CreativeIndexBuilder.fill(entry, item.stack, languages, codes, settings,
                        tooltipContext, player, englishSearched);
                return entry;
            } catch (RuntimeException | LinkageError error) {
                FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] skipped stored item: {}",
                        BetterSearch.MOD_NAME, error.toString());
                return null;
            }
        }, false);
    }

    private static final class StackKey {
        private final ItemStack stack;
        private final Object item;
        private final Object data;
        private final int hash;

        private StackKey(ItemStack stack) {
            this.stack = stack;
            item = stack.getItem();
            data = stack.getComponentsPatch();
            hash = 31 * System.identityHashCode(item) + Objects.hashCode(data);
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
            if (!(other instanceof StackKey)) {
                return false;
            }
            StackKey key = (StackKey) other;
            return item == key.item && Objects.equals(data, key.data);
        }
    }
}
