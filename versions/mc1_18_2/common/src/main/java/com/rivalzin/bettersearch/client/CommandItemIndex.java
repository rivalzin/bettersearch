package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.async.StagedSupplier;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.core.Registry;
import com.rivalzin.bettersearch.client.gui.ComponentCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

public final class CommandItemIndex {
    private static final AsyncIndexState<CommandIndex> INDEX = new AsyncIndexState<>(
            task -> Minecraft.getInstance().execute(task), task -> Util.backgroundExecutor().execute(task),
            task -> Minecraft.getInstance().execute(task),
            error -> BetterSearch.LOGGER.error("[{}] item index failed", BetterSearch.MOD_NAME, error));

    private CommandItemIndex() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    public static List<ResourceLocation> search(String rawQuery) {
        return search(rawQuery, BetterSearchClient.settings().searchCommandItems, false);
    }

    public static List<ResourceLocation> searchBlocks(String rawQuery) {
        return search(rawQuery, BetterSearchClient.settings().searchCommandItems, true);
    }

    public static List<ResourceLocation> search(String rawQuery, boolean allowed) {
        return search(rawQuery, allowed, false);
    }

    private static List<ResourceLocation> search(String rawQuery, boolean allowed, boolean blocksOnly) {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !allowed) {
            return null;
        }
        int size = Registry.ITEM.size();
        CommandIndex current = INDEX.getPrepared(Registry.ITEM, size, BetterSearchClient.languageStamp(),
                () -> prepare(BetterSearchClient.languages(), settings.copy()), null);
        if (current == null) {
            return null;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            List<ResourceLocation> results = current.index.search(query, settings);
            if (!blocksOnly) {
                return results;
            }
            List<ResourceLocation> blocks = new ArrayList<>(results.size());
            for (ResourceLocation id : results) {
                if (current.blockIds.contains(id)) {
                    blocks.add(id);
                }
            }
            return blocks;
        } catch (RuntimeException | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.error("[{}] item id search failed", BetterSearch.MOD_NAME, error);
            return null;
        }
    }

    private static StagedSupplier<CommandIndex> prepare(LanguageTable languages, SearchSettings settings) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        List<Item> items = new ArrayList<>();
        for (Item item : Registry.ITEM) {
            items.add(item);
        }
        Set<ResourceLocation> blocks = new HashSet<>();
        StagedSupplier<SearchIndex<ResourceLocation>> captured = EntrySnapshot.capture(items, item -> {
            try {
                ResourceLocation id = Registry.ITEM.getKey(item);
                if (id == null) {
                    return null;
                }
                EntrySnapshot<ResourceLocation> builder = new EntrySnapshot<>(id);
                builder.modId(id.getNamespace());
                builder.family(id.getPath());

                String descriptionId = item.getDescriptionId();
                builder.add(ComponentCompat.translatable(descriptionId).getString(), SearchField.SOURCE_NATIVE);
                for (String code : codes) {
                    String translated = languages.get(code, descriptionId);
                    if (translated != null) {
                        builder.add(translated, code.equals("en_us")
                                ? SearchField.SOURCE_ENGLISH
                                : SearchField.SOURCE_FOREIGN);
                    }
                }
                builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
                if (item instanceof BlockItem) {
                    blocks.add(id);
                }
                return builder;
            } catch (RuntimeException | LinkageError t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.debug("[{}] skipped item in command index: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
            return null;
        }, false);
        return new StagedSupplier<CommandIndex>() {
            @Override
            public boolean advance() {
                return captured.advance();
            }

            @Override
            public CommandIndex get() {
                return new CommandIndex(captured.get(), Collections.unmodifiableSet(blocks));
            }
        };
    }

    private static final class CommandIndex {
        final SearchIndex<ResourceLocation> index;
        final Set<ResourceLocation> blockIds;

        CommandIndex(SearchIndex<ResourceLocation> index, Set<ResourceLocation> blockIds) {
            this.index = index;
            this.blockIds = blockIds;
        }
    }
}
