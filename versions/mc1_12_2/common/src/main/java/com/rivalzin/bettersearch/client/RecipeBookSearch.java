package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.async.EntrySnapshot;
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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RecipeBookSearch {
    private static final AsyncIndexState<SearchIndex<RecipeList>> INDEX = new AsyncIndexState<>(
            task -> Minecraft.getMinecraft().addScheduledTask(task), ForkJoinPool.commonPool(),
            task -> Minecraft.getMinecraft().addScheduledTask(task),
            error -> BetterSearch.LOGGER.error("[{}] failed to build recipe index", BetterSearch.MOD_NAME, error));
    private static final AtomicBoolean checking = new AtomicBoolean();
    private static volatile Context context;

    private RecipeBookSearch() {
    }

    public static List<RecipeList> search(String query) {
        SearchSettings settings = ModConfig.settings();
        if (query == null || !settings.enabled || !settings.searchRecipeBook) {
            return null;
        }
        ensureIndex();
        Context captured = context;
        SearchIndex<RecipeList> current = captured != null && captured.matches()
                ? INDEX.ready(captured, 0, 0) : null;
        if (current == null) {
            return null;
        }
        try {
            SearchQuery parsed = SearchQuery.parse(query, settings);
            return parsed.isEmpty() ? null : current.search(parsed, settings);
        } catch (RuntimeException | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.error("[{}] recipe search failed, falling back to vanilla", BetterSearch.MOD_NAME, error);
            return null;
        }
    }

    private static void ensureIndex() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    refresh();
                } finally {
                    checking.set(false);
                }
            });
        } catch (RuntimeException | LinkageError error) {
            checking.set(false);
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            throw error;
        }
    }

    private static void refresh() {
        List<RecipeList> source = RecipeBookClient.ALL_RECIPES;
        if (source == null || source.isEmpty() || Minecraft.getMinecraft().player == null) {
            INDEX.invalidate();
            context = null;
            return;
        }
        SearchSettings settings = ModConfig.settings();
        LangTable.ensure(settings);
        Context current = context;
        if (current == null || !current.matches()) {
            INDEX.invalidate();
            current = new Context(source);
            context = current;
        }
        INDEX.getPrepared(current, 0, 0, () -> {
            List<RecipeList> snapshot = new ArrayList<>(source);
            List<String> codes = LangTable.activeCodes(settings);
            return EntrySnapshot.capture(snapshot, list -> {
                try {
                    EntrySnapshot<RecipeList> builder = new EntrySnapshot<>(list);
                    for (IRecipe recipe : list.getRecipes()) {
                        ItemStack out = recipe.getRecipeOutput();
                        if (out != null && !out.isEmpty()) {
                            CreativeIndex.fill(builder, out, settings, codes, out.getDisplayName(), null);
                        }
                    }
                    return builder;
                } catch (RuntimeException | LinkageError error) {
                    com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] recipe skipped in index: {}", BetterSearch.MOD_NAME, error.toString());
                    return null;
                }
            });
        }, null);
    }

    private static final class Context {
        final Object source;
        final int size;
        final java.lang.ref.WeakReference<Object> player = new java.lang.ref.WeakReference<>(Minecraft.getMinecraft().player);
        final String language = Minecraft.getMinecraft().gameSettings.language;
        final int languageStamp = LangTable.stamp();
        final int configStamp = ModConfig.stamp();

        Context(List<RecipeList> source) {
            this.source = source;
            this.size = source.size();
        }

        boolean matches() {
            List<RecipeList> current = RecipeBookClient.ALL_RECIPES;
            return source == current && current.size() == size && player.get() == Minecraft.getMinecraft().player
                    && language.equals(Minecraft.getMinecraft().gameSettings.language)
                    && languageStamp == LangTable.stamp() && configStamp == ModConfig.stamp();
        }
    }
}
