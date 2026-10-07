package com.rivalzin.bettersearch.client.simplestorage;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.rivalzin.bettersearch.client.*;
import com.rivalzin.bettersearch.compat.SimpleStorageController;
import net.minecraft.client.gui.components.EditBox;
import java.util.concurrent.Executor;

public final class SimpleStorageSearch {
    private static final SimpleStorageController CONTROLLER = new SimpleStorageController(new Platform());

    private SimpleStorageSearch() {
    }

    public static void begin(Object widget, List<?> stacks, Object searchBar) {
        CONTROLLER.begin(widget, stacks, searchBar instanceof EditBox ? ((EditBox) searchBar).getValue() : "");
    }

    public static boolean matches(boolean nativeMatch, Object widget, Object stack) {
        return CONTROLLER.matches(nativeMatch, widget, stack);
    }

    public static void changed(Object widget) { CONTROLLER.changed(widget); }
    public static void switchScreen(Object screen) { CONTROLLER.switchScreen(screen); }

    private static final class Platform implements SimpleStorageController.Platform {
        @Override
        public Object screen() { return Minecraft.getInstance().screen; }
        @Override
        public SearchSettings settings() { return BetterSearchClient.settings(); }
        @Override
        public long languageRevision(SearchSettings settings) {
            if (settings.enabled && settings.crossLanguage) BetterSearchClient.ensureLanguagesLoaded();
            return BetterSearchClient.languageStamp();
        }
        @Override
        public Object key(Object value) {
            return value instanceof ItemStack && !((ItemStack) value).isEmpty() ? new StackKey((ItemStack) value) : null;
        }
        @Override
        public Object snapshot(Object value) {
            ItemStack copy = ((StackKey) value).stack.copy();
            copy.setCount(1);
            return new StackKey(copy);
        }
        @Override
        public Executor client() { return task -> Minecraft.getInstance().execute(task); }
        @Override
        public Executor worker() { return task -> Util.backgroundExecutor().execute(task); }
        @Override
        public void report(Throwable error) {
            FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.debug("[{}] Simple Storage Network search fallback: {}", BetterSearch.MOD_NAME, error.toString());
        }
        @Override
        public Supplier<SearchIndex<Object>> prepare(List<Object> items, SearchSettings settings) {
            Minecraft minecraft = Minecraft.getInstance();
            Player player = minecraft.player;
            if (player == null) {
                return () -> new SearchIndex<>(java.util.Collections.emptyList(), false);
            }
            LanguageTable languages = BetterSearchClient.languages();
            List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
            boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);
            return EntrySnapshot.capture(items, item -> {
                try {
                    EntrySnapshot<Object> entry = new EntrySnapshot<>(item);
                    CreativeIndexBuilder.fill(entry, ((StackKey) item).stack, languages, codes, settings,
                            player, englishSearched);
                    return entry;
                } catch (RuntimeException | LinkageError error) {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] skipped stored item: {}",
                            BetterSearch.MOD_NAME, error.toString());
                    return null;
                }
            }, false);
        }
    
    }

    private static final class StackKey {
        private final ItemStack stack;
        private final Object item;
        private final Object data;
        private final int hash;

        private StackKey(ItemStack stack) {
            this.stack = stack;
            item = stack.getItem();
            data = stack.getTag();
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
            return ItemStack.isSameItemSameTags(stack, key.stack);
        }
    }
}
