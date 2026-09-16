package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
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
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

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
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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

        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<RecipeCollection> ready = INDEX.ready(collections, collections.size(), stamp);
        if (ready != null) {
            return ready;
        }

        return INDEX.getPrepared(collections, collections.size(), stamp, () -> {
            final List<RecipeCollection> snapshot = List.copyOf(collections);
            final LanguageTable languages = BetterSearchClient.languages();
            final SearchSettings snapshotSettings = settings.copy();
            return prepare(snapshot, languages, snapshotSettings);
        });
    }

    private static java.util.function.Supplier<SearchIndex<RecipeCollection>> prepare(List<RecipeCollection> collections,
                                                       LanguageTable languages,
                                                       SearchSettings settings) {
        List<String> codes = new ArrayList<>();
        for (String code : languages.languageCodes()) {
            if (settings.indexesLanguage(code)) {
                codes.add(code);
            }
        }

        return EntrySnapshot.capture(collections, collection -> {
            try {
                EntrySnapshot<RecipeCollection> builder = new EntrySnapshot<>(collection);

                for (Recipe<?> recipe : collection.getRecipes()) {
                    ItemStack result = recipe.getResultItem(collection.registryAccess());
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

                        builder.modId(id.getNamespace());
                        builder.family(id.getPath());
                        if (settings.searchItemIds) {
                            builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                                    SearchField.SOURCE_ID);
                        }
                    }
                }
                if (!builder.isEmpty()) {
                    return builder;
                }
            } catch (RuntimeException | LinkageError t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.debug("[{}] skipped recipe group: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
            return null;
        });
    }
}
