package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

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

    public static void warmUp() {
        if (!ModConfig.settings().enabled || !ModConfig.settings().searchCreative) {
            return;
        }
        ensureIndex();
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

    public static List<ItemStack> searchForViewer(String query) {
        SearchSettings settings = ModConfig.settings();
        if (query == null || !settings.enabled || !settings.searchJei) {
            return null;
        }
        SearchIndex<ItemStack> current = index;
        if (current == null) {
            return null;
        }
        try {
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }
            return current.search(parsed, settings);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] NEI search failed, filter contributes nothing: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    public static Object currentIndex() {
        return index;
    }

    public static String stackKey(ItemStack stack) {
        String id = Item.itemRegistry.getNameForObject(stack.getItem());
        return id + '@' + stack.getItemDamage() + '#'
                + (stack.hasTagCompound() ? String.valueOf(stack.getTagCompound()) : "");
    }

    private static void ensureIndex() {
        LangTable.ensure(ModConfig.settings());
        String language = Minecraft.getMinecraft().gameSettings.language;
        int stamp = LangTable.stamp() + ModConfig.stamp() * 100_000;
        if (building || (index != null && indexLanguage.equals(language) && indexStamp == stamp)) {
            return;
        }
        building = true;

        final List<ItemStack> snapshot = collectLikeVanilla();
        final String buildLanguage = language;
        final int buildStamp = stamp;

        Thread worker = new Thread(() -> {
            try {
                SearchIndex<ItemStack> renamed = CreativeIndex.build(snapshot, ModConfig.settings());
                index = renamed;
                indexLanguage = buildLanguage;
                indexStamp = buildStamp;
            } catch (Throwable t) {
                BetterSearch.LOGGER.error("[{}] failed to build creative index",
                        BetterSearch.MOD_NAME, t);
            } finally {
                building = false;
            }
        }, "BetterSearch-Indice-1.7.10");
        worker.setDaemon(true);
        worker.start();
    }

    static List<ItemStack> collectLikeVanilla() {
        List<ItemStack> source = new ArrayList<ItemStack>();

        java.util.Iterator<?> it = Item.itemRegistry.iterator();
        while (it.hasNext()) {
            Item item = (Item) it.next();
            if (item == null || item.getCreativeTab() == null) {
                continue;
            }
            try {
                item.getSubItems(item, (CreativeTabs) null, source);
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped modded item: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        for (Enchantment enchantment : Enchantment.enchantmentsList) {
            if (enchantment != null && enchantment.type != null) {
                try {
                    Items.enchanted_book.func_92113_a(enchantment, source);
                } catch (Throwable t) {
                    BetterSearch.LOGGER.debug("[{}] skipped enchantment: {}",
                            BetterSearch.MOD_NAME, t.toString());
                }
            }
        }
        return source;
    }
}
