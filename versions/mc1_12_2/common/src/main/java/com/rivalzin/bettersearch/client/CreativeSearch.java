package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.List;

public final class CreativeSearch {
    private static volatile SearchIndex<ItemStack> index;
    private static volatile String indexLanguage = "";
    private static volatile int indexStamp = -1;
    private static volatile boolean building;

    private static SearchIndex<ItemStack> cachedIndex;
    private static String cachedQuery;
    private static List<ItemStack> cachedResult;

    private CreativeSearch() {
    }

    public static List<ItemStack> search(String query) {
        if (query == null || !ModConfig.settings().enabled || !ModConfig.settings().searchCreative) {
            return null;
        }
        ensureIndex();
        SearchIndex<ItemStack> current = index;
        if (current == null) {
            return null;
        }
        if (current == cachedIndex && query.equals(cachedQuery) && cachedResult != null) {
            return cachedResult;
        }
        try {
            SearchQuery parsed = SearchQuery.parse(query, ModConfig.settings());
            if (parsed.isEmpty()) {
                return null;
            }
            List<ItemStack> result = current.search(parsed, ModConfig.settings());
            cachedIndex = current;
            cachedQuery = query;
            cachedResult = result;
            return result;
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] creative search failed, falling back to vanilla",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static void ensureIndex() {
        LangTable.ensure(ModConfig.settings());
        String language = Minecraft.getMinecraft().gameSettings.language;
        int stamp = LangTable.stamp() + ModConfig.stamp() * 100_000;
        if (building || (index != null && indexLanguage.equals(language) && indexStamp == stamp)) {
            return;
        }
        building = true;

        final NonNullList<ItemStack> source = NonNullList.create();
        for (Item item : Item.REGISTRY) {
            try {
                item.getSubItems(CreativeTabs.SEARCH, source);
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped modded item: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        final List<ItemStack> snapshot = new ArrayList<>(source);
        final String buildLanguage = language;

        Thread worker = new Thread(() -> {
            try {
                SearchIndex<ItemStack> renamed = CreativeIndex.build(snapshot, ModConfig.settings());
                index = renamed;
                indexLanguage = buildLanguage;
                indexStamp = stamp;
            } catch (Throwable t) {
                BetterSearch.LOGGER.error("[{}] failed to build creative index",
                        BetterSearch.MOD_NAME, t);
            } finally {
                building = false;
            }
        }, "BetterSearch-Indice-1.12.2");
        worker.setDaemon(true);
        worker.start();
    }
}
