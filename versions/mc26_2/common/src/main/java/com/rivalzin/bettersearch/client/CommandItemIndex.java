package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ids only, tooltips are useless in a command suggestion
public final class CommandItemIndex {
    private static final AsyncIndex<Identifier> INDEX = new AsyncIndex<>("item ids");

    // /setblock wants a block, and this index is the item list
    private static volatile Set<Identifier> blockIds = Collections.emptySet();

    private CommandItemIndex() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    public static List<Identifier> search(String rawQuery) {
        return search(rawQuery, BetterSearchClient.settings().searchCommandItems);
    }

    public static List<Identifier> searchBlocks(String rawQuery) {
        List<Identifier> all = search(rawQuery);
        if (all == null) {
            return null;
        }
        Set<Identifier> blocks = blockIds;
        List<Identifier> out = new ArrayList<>(all.size());
        for (Identifier id : all) {
            if (blocks.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    public static List<Identifier> search(String rawQuery, boolean allowed) {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !allowed) {
            return null;
        }

        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings snapshot = settings.copy();
        int size = BuiltInRegistries.ITEM.size();

        SearchIndex<Identifier> index = INDEX.get(BuiltInRegistries.ITEM, size,
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

    private static SearchIndex<Identifier> build(LanguageTable languages, SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        List<SearchIndex.Entry<Identifier>> entries = new ArrayList<>(BuiltInRegistries.ITEM.size());
        Set<Identifier> blocks = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }
                EntryBuilder<Identifier> builder = new EntryBuilder<>(id);
                builder.modId(id.getNamespace());
                builder.family(id.getPath());

                String descriptionId = item.getDescriptionId();
                builder.add(Component.translatable(descriptionId).getString(), SearchField.SOURCE_NATIVE);
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
