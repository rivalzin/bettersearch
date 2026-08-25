package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

// the recipe book searches collections, not single items
public final class RecipeSearch {
    private static final AsyncIndex<RecipeCollection> INDEX = new AsyncIndex<>("recipes");
    private static boolean loggedActive;

    private RecipeSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
        loggedActive = false;
    }

    public static void prepare() {
        ensureIndex();
    }

    public static List<RecipeCollection> search(String rawQuery) {
        SearchIndex<RecipeCollection> index = ensureIndex();
        if (index == null || index.size() == 0) {
            return null;
        }
        try {
            SearchSettings settings = BetterSearchClient.settings();
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            if (!loggedActive) {
                loggedActive = true;
                BetterSearch.LOGGER.info("[{}] recipe book search ready ({} groups indexed)",
                        BetterSearch.MOD_NAME, index.size());
            }
            return index.search(query, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] recipe search failed", BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static SearchIndex<RecipeCollection> ensureIndex() {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !settings.searchRecipeBook) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        ClientRecipeBook book = minecraft.player.getRecipeBook();
        List<RecipeCollection> collections = book.getCollections();
        if (collections.isEmpty()) {
            return null;
        }

        // the recipe book calls this every tick while it is open, so nothing is copied
        // until there is a reason to build
        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<RecipeCollection> ready = INDEX.ready(collections, collections.size(), stamp);
        if (ready != null) {
            return ready;
        }

        final List<RecipeCollection> snapshot = List.copyOf(collections);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings snapshotSettings = settings.copy();

        return INDEX.get(collections, collections.size(), stamp,
                () -> build(snapshot, languages, snapshotSettings));
    }

    private static SearchIndex<RecipeCollection> build(List<RecipeCollection> collections,
                                                       LanguageTable languages,
                                                       SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        List<SearchIndex.Entry<RecipeCollection>> entries = new ArrayList<>(collections.size());
        int skipped = 0;
        for (RecipeCollection collection : collections) {
            try {
                EntryBuilder<RecipeCollection> builder = new EntryBuilder<>(collection);
                for (RecipeHolder<?> holder : collection.getRecipes()) {
                    ItemStack result = holder.value().getResultItem(collection.registryAccess());
                    if (result.isEmpty()) {
                        continue;
                    }
                    builder.add(result.getHoverName().getString(), SearchField.SOURCE_NATIVE);

                    String descriptionId = result.getDescriptionId();
                    for (String code : codes) {
                        String translated = languages.get(code, descriptionId);
                        if (translated != null) {
                            builder.add(translated, code.equals("en_us")
                                    ? SearchField.SOURCE_ENGLISH
                                    : SearchField.SOURCE_FOREIGN);
                        }
                    }

                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
                    if (id != null) {
                        // outside the if: the mod filter and the kind of item are not the id
                        // text, and without them a recipe loses its group and its @mod
                        builder.modId(id.getNamespace());
                        builder.family(id.getPath());
                        if (settings.searchItemIds) {
                            builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                                    SearchField.SOURCE_ID);
                        }
                    }
                }
                if (builder.isEmpty()) {
                    skipped++;
                } else {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                skipped++;
                BetterSearch.LOGGER.debug("[{}] skipped recipe group: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        BetterSearch.LOGGER.info("[{}] recipe index ready: {} groups ({} with no usable result)",
                BetterSearch.MOD_NAME, entries.size(), skipped);
        return new SearchIndex<>(entries);
    }
}
