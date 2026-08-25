package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
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

// ids only, tooltips are useless in a command suggestion
public final class CommandItemIndex {
    private static final AsyncIndex<ResourceLocation> INDEX = new AsyncIndex<>("item ids");

    // /setblock wants a block, and this index is the item list
    private static volatile Set<ResourceLocation> blockIds = Collections.emptySet();

    private CommandItemIndex() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    public static List<ResourceLocation> search(String rawQuery) {
        return search(rawQuery, BetterSearchClient.settings().searchCommandItems);
    }

    public static List<ResourceLocation> searchBlocks(String rawQuery) {
        List<ResourceLocation> all = search(rawQuery);
        if (all == null) {
            return null;
        }
        Set<ResourceLocation> blocks = blockIds;
        List<ResourceLocation> out = new ArrayList<>(all.size());
        for (ResourceLocation id : all) {
            if (blocks.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    public static List<ResourceLocation> search(String rawQuery, boolean allowed) {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !allowed) {
            return null;
        }

        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings snapshot = settings.copy();
        int size = Registry.ITEM.keySet().size();

        SearchIndex<ResourceLocation> index = INDEX.get(Registry.ITEM, size,
                BetterSearchClient.languageStamp(), () -> build(languages, snapshot));
        if (index == null) {
            return null;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            return index.search(query, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] item id search failed", BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static SearchIndex<ResourceLocation> build(LanguageTable languages, SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        List<SearchIndex.Entry<ResourceLocation>> entries = new ArrayList<>(Registry.ITEM.keySet().size());
        Set<ResourceLocation> blocks = new HashSet<>();
        for (Item item : Registry.ITEM) {
            try {
                ResourceLocation id = Registry.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }
                EntryBuilder<ResourceLocation> builder = new EntryBuilder<>(id);
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
                entries.add(builder.build());
                if (item instanceof BlockItem) {
                    blocks.add(id);
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped item in command index: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        blockIds = blocks;
        BetterSearch.LOGGER.info("[{}] item id index ready: {} entries",
                BetterSearch.MOD_NAME, entries.size());
        // false: the suggestion box stops at twelve lines, and grouping there only
        // pushes the name being typed past the end of it
        return new SearchIndex<>(entries, false);
    }
}
