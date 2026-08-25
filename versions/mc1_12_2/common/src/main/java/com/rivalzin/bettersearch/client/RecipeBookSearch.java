package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.recipebook.RecipeList;
import net.minecraft.client.util.RecipeBookClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

import java.util.ArrayList;
import java.util.List;

public final class RecipeBookSearch {
    private static volatile SearchIndex<RecipeList> index;
    private static volatile String indexLanguage = "";
    private static volatile int indexStamp = -1;
    private static volatile int indexSize = -1;
    // the client tick and the viewer thread both come through here, and a plain
    // read-then-write let the two of them start the same work twice
    private static final java.util.concurrent.atomic.AtomicBoolean building =
            new java.util.concurrent.atomic.AtomicBoolean();

    private RecipeBookSearch() {
    }

    public static List<RecipeList> search(String query) {
        if (query == null || !ModConfig.settings().enabled || !ModConfig.settings().searchRecipeBook) {
            return null;
        }
        ensureIndex();
        SearchIndex<RecipeList> current = index;
        if (current == null) {
            return null;
        }
        try {
            SearchQuery parsed = SearchQuery.parse(query, ModConfig.settings());
            if (parsed.isEmpty()) {
                return null;
            }
            return current.search(parsed, ModConfig.settings());
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] recipe search failed, falling back to vanilla",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static void ensureIndex() {
        List<RecipeList> source = RecipeBookClient.ALL_RECIPES;
        if (source == null || source.isEmpty()) {
            return;
        }
        LangTable.ensure(ModConfig.settings());
        String language = Minecraft.getMinecraft().gameSettings.language;
        int stamp = LangTable.stamp() + ModConfig.stamp() * 100_000;
        if (building.get() || (index != null && indexLanguage.equals(language)
                && indexSize == source.size() && indexStamp == stamp)) {
            return;
        }
        if (!building.compareAndSet(false, true)) {
            return;
        }
        boolean queued = false;
        try {

            final List<RecipeList> snapshot = new ArrayList<>(source);
            final String buildLanguage = language;
            // read once here, on the client thread: the worker must not touch the game settings
            final SearchSettings settings = ModConfig.settings();
            final List<String> codes = LangTable.activeCodes(settings);

            Thread worker = new Thread(() -> {
                try {
                    long started = System.nanoTime();
                    List<SearchIndex.Entry<RecipeList>> entries = new ArrayList<>(snapshot.size());
                    for (RecipeList list : snapshot) {
                        try {
                            EntryBuilder<RecipeList> builder = new EntryBuilder<>(list);
                            for (IRecipe recipe : list.getRecipes()) {
                                ItemStack out = recipe.getRecipeOutput();
                                if (out != null && !out.isEmpty()) {
                                    CreativeIndex.fill(builder, out, settings, codes, null);
                                }
                            }
                            if (!builder.isEmpty()) {
                                entries.add(builder.build());
                            }
                        } catch (Throwable t) {
                            BetterSearch.LOGGER.debug("[{}] recipe skipped in index: {}",
                                    BetterSearch.MOD_NAME, t.toString());
                        }
                    }
                    index = new SearchIndex<>(entries);
                    indexLanguage = buildLanguage;
                    indexStamp = stamp;
                    indexSize = snapshot.size();
                    BetterSearch.LOGGER.info("[{}] recipe index ready (1.12.2): {} lists in {} ms",
                            BetterSearch.MOD_NAME, entries.size(), (System.nanoTime() - started) / 1_000_000);
                } catch (Throwable t) {
                    BetterSearch.LOGGER.error("[{}] failed to build recipe index",
                            BetterSearch.MOD_NAME, t);
                } finally {
                    building.set(false);
                }
            }, "BetterSearch-Receitas-1.12.2");
            worker.setDaemon(true);
            worker.start();
            queued = true;
        } finally {
            // nothing was queued, so the flag has to come back down here:
            // otherwise one throw closes this path for the rest of the session
            if (!queued) {
                building.set(false);
            }
        }
    }
}
