package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.compat.ChestSearchCache;
import com.rivalzin.bettersearch.compat.ChestSearchOptions;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChestSearchBarSearch {
    private final ChestSearchCache<StackKey> cache = new ChestSearchCache<>(4096);
    private SearchSettings settings;
    private LanguageTable languages;
    private List<String> codes = Collections.emptyList();
    private boolean englishSearched;
    private long languageRevision = Long.MIN_VALUE;
    private String currentLanguage;
    private boolean failed;

    public void begin(String query) {
        cache.end();
        if (!com.rivalzin.bettersearch.async.StorageSearchSession.isPlainTerm(query)) {
            return;
        }
        SearchSettings next = BetterSearchClient.settings();
        next.enabled &= next.searchChestSearchBar;
        if (!next.enabled) {
            close();
            return;
        }
        next.searchTooltips &= ChestSearchOptions.allowsTooltips();
        if (next.enabled && next.crossLanguage) {
            BetterSearchClient.ensureLanguagesLoaded();
        }
        long revision = BetterSearchClient.languageStamp();
        String language = LanguageCatalog.currentCode();
        boolean changed = settings == null || !settings.equals(next)
                || languageRevision != revision || !Objects.equals(currentLanguage, language);
        if (!Objects.equals(currentLanguage, language)) {
            cache.clear();
        }
        settings = next;
        languageRevision = revision;
        currentLanguage = language;
        if (changed) {
            languages = settings.crossLanguage ? BetterSearchClient.languages() : LanguageTable.EMPTY;
            codes = CreativeIndexBuilder.activeCodes(languages, settings);
            englishSearched = CreativeIndexBuilder.englishSearched(codes);
            failed = false;
        }
        cache.begin(query, settings, revision);
    }

    public boolean matches(ItemStack stack, String query) {
        if (failed || !cache.active() || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            StackKey lookup = new StackKey(stack);
            return cache.matches(query, lookup, () -> new StackKey(stack.copy()), this::build);
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            failed = true;
            cache.clear();
            BetterSearch.LOGGER.warn("[{}] could not extend Chest Search Bar search", BetterSearch.MOD_NAME, error);
            return false;
        }
    }

    public void end() {
        cache.end();
    }

    public void close() {
        cache.clear();
        settings = null;
        languages = null;
        codes = Collections.emptyList();
    }

    private SearchIndex<StackKey> build(StackKey key) {
        Minecraft minecraft = Minecraft.getInstance();
        EntrySnapshot<StackKey> entry = new EntrySnapshot<>(key);
        CreativeIndexBuilder.fill(entry, key.stack, languages, codes, settings,
                Item.TooltipContext.of(minecraft.level), minecraft.player, englishSearched);
        return new SearchIndex<>(Collections.singletonList(entry.build()), false);
    }

    private static final class StackKey {
        final ItemStack stack;
        final Object item;
        final Object data;
        final int hash;

        StackKey(ItemStack stack) {
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
