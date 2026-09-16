package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CreativeSearch {
    private static final AsyncIndexState<SearchIndex<ItemStack>> INDEX = new AsyncIndexState<>(
            task -> Minecraft.getMinecraft().addScheduledTask(task), ForkJoinPool.commonPool(),
            task -> Minecraft.getMinecraft().addScheduledTask(task),
            error -> BetterSearch.LOGGER.error("[{}] failed to build creative index", BetterSearch.MOD_NAME, error));
    private static final AtomicBoolean checking = new AtomicBoolean();
    private static volatile Context context;
    private static volatile Cache cache;
    private static volatile int generation;

    private CreativeSearch() {
    }

    public static int generation() {
        return generation;
    }

    public static void warmUp() {
        SearchSettings settings = ModConfig.settings();
        if (settings.enabled && (settings.searchCreative || settings.searchJei)) {
            ensureIndex();
        }
    }

    public static List<ItemStack> search(String query) {
        SearchSettings settings = ModConfig.settings();
        if (query == null || !settings.enabled || !settings.searchCreative) {
            return null;
        }
        ensureIndex();
        return search(query, settings, current());
    }

    private static List<ItemStack> search(String query, SearchSettings settings, SearchIndex<ItemStack> current) {
        if (current == null) {
            return null;
        }
        Cache cached = cache;
        int stamp = ModConfig.stamp();
        if (cached != null && cached.index == current && cached.stamp == stamp && query.equals(cached.query)) {
            return cached.results;
        }
        try {
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }
            List<ItemStack> results = current.search(parsed, settings);
            cache = new Cache(current, query, stamp, results);
            return results;
        } catch (RuntimeException | LinkageError error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.error("[{}] creative search failed, falling back to vanilla", BetterSearch.MOD_NAME, error);
            return null;
        }
    }

    private static SearchIndex<ItemStack> current() {
        Context current = context;
        return current != null && current.matches()
                ? INDEX.ready(current, 0, 0) : null;
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
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            INDEX.invalidate();
            context = null;
            cache = null;
            return;
        }
        SearchSettings settings = ModConfig.settings();
        LangTable.ensure(settings);
        Context current = context;
        if (current == null || !current.matches()) {
            INDEX.invalidate();
            current = new Context();
            context = current;
            cache = null;
        }
        INDEX.getPrepared(current, 0, 0,
                () -> CreativeIndex.prepare(collectLikeVanilla(), settings), () -> {
                    cache = null;
                    generation++;
                });
    }

    private static final class Context {
        private final String language = Minecraft.getMinecraft().gameSettings.language;
        private final int languageStamp = LangTable.stamp();
        private final int configStamp = ModConfig.stamp();
        private final java.lang.ref.WeakReference<Object> player = new java.lang.ref.WeakReference<>(Minecraft.getMinecraft().player);

        boolean matches() {
            Minecraft minecraft = Minecraft.getMinecraft();
            return language.equals(minecraft.gameSettings.language) && languageStamp == LangTable.stamp()
                    && configStamp == ModConfig.stamp() && player.get() == minecraft.player;
        }
    }

    private static final class Cache {
        final SearchIndex<ItemStack> index;
        final String query;
        final int stamp;
        final List<ItemStack> results;

        Cache(SearchIndex<ItemStack> index, String query, int stamp, List<ItemStack> results) {
            this.index = index;
            this.query = query;
            this.stamp = stamp;
            this.results = results;
        }
    }

    private static List<ItemStack> collectLikeVanilla() {
        net.minecraft.util.NonNullList<ItemStack> source = net.minecraft.util.NonNullList.create();
        for (Item item : Item.REGISTRY) {
            try {
                item.getSubItems(CreativeTabs.SEARCH, source);
            } catch (RuntimeException | LinkageError error) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] skipped modded item: {}", BetterSearch.MOD_NAME, error.toString());
            }
        }
        return new ArrayList<>(source);
    }
}
