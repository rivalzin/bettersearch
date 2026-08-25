package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.client.LangTable;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class JeiSearchBridge {
    private static final int MAX_TOOLTIP_LINES = 6;

    private static volatile SearchIndex<Integer> index;
    private static volatile String indexLanguage = "";
    private static volatile int indexStamp = -1;
    private static volatile int indexSize = -1;
    // the client tick and the viewer thread both come through here, and a plain
    // read-then-write let the two of them start the same work twice
    private static final java.util.concurrent.atomic.AtomicBoolean building =
            new java.util.concurrent.atomic.AtomicBoolean();

    // bumped when a new index is ready, so JeiIntegration knows to poke JEI
    private static volatile int generation;

    private static Field itemListField;

    private static SearchIndex<Integer> cachedIndex;
    private static String cachedWord;
    private static int[] cachedResult;

    private JeiSearchBridge() {
    }

    static int generation() {
        return generation;
    }

    static int[] search(String word, IngredientFilter filter) {
        SearchSettings settings = ModConfig.settings();
        if (word == null || word.isEmpty() || !settings.enabled || !settings.searchJei) {
            return null;
        }
        try {
            // JEI hands us indexes, not stacks - the ints are positions in its list
            ensureIndex(filter, settings);
            SearchIndex<Integer> current = index;
            if (current == null) {
                return null;
            }
            if (current == cachedIndex && word.equals(cachedWord) && cachedResult != null) {
                return cachedResult;
            }
            SearchQuery query = SearchQuery.parse(word, settings);
            if (query.isEmpty()) {
                return null;
            }
            List<Integer> found = current.search(query, settings);
            int[] result = new int[found.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = found.get(i);
            }
            cachedIndex = current;
            cachedWord = word;
            cachedResult = result;
            return result;
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] JEI search failed, falling back",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static void ensureIndex(IngredientFilter filter, SearchSettings settings) throws Exception {
        LangTable.ensure(settings);
        if (itemListField == null) {
            itemListField = IngredientFilter.class.getDeclaredField("elementList");
            itemListField.setAccessible(true);
        }
        List<?> list = (List<?>) itemListField.get(filter);
        if (list == null) {
            return;
        }
        String language = Minecraft.getMinecraft().gameSettings.language;
        int stamp = LangTable.stamp() + ModConfig.stamp() * 100_000;
        int size = list.size();
        if (building.get() || (index != null && indexLanguage.equals(language)
                && indexStamp == stamp && indexSize == size)) {
            return;
        }
        if (!building.compareAndSet(false, true)) {
            return;
        }
        boolean queued = false;
        try {

            final List<Object> snapshot = new ArrayList<>(list);
            final String buildLanguage = language;

            Thread worker = new Thread(() -> {
                try {
                    index = build(snapshot, ModConfig.settings());
                    indexLanguage = buildLanguage;
                    indexStamp = stamp;
                    indexSize = snapshot.size();
                    generation++;
                } catch (Throwable t) {
                    BetterSearch.LOGGER.error("[{}] failed to build JEI index",
                            BetterSearch.MOD_NAME, t);
                } finally {
                    building.set(false);
                }
            }, "BetterSearch-Indice-JEI-1.12.2");
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

    private static SearchIndex<Integer> build(List<Object> elements, SearchSettings settings) {
        long started = System.nanoTime();
        List<SearchIndex.Entry<Integer>> entries = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            try {
                IIngredientListElement<?> element = (IIngredientListElement<?>) elements.get(i);
                EntryBuilder<Integer> builder = new EntryBuilder<>(i);
                fill(builder, element, settings);
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped JEI ingredient: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }
        SearchIndex<Integer> renamed = new SearchIndex<>(entries);
        BetterSearch.LOGGER.info("[{}] JEI index ready (1.12.2): {} ingredients in {} ms",
                BetterSearch.MOD_NAME, entries.size(), (System.nanoTime() - started) / 1_000_000);
        return renamed;
    }

    private static void fill(EntryBuilder<Integer> builder, IIngredientListElement<?> element,
                                  SearchSettings settings) {
        builder.add(element.getDisplayName(), SearchField.SOURCE_NATIVE);

        Object ingredient = element.getIngredient();
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;

            if (settings.crossLanguage) {
                String key = stack.getTranslationKey() + ".name";
                for (String code : LangTable.activeCodes(settings)) {
                    String translated = LangTable.get(code, key);
                    if (translated != null) {
                        builder.add(translated, "en_us".equalsIgnoreCase(code)
                                ? SearchField.SOURCE_ENGLISH
                                : SearchField.SOURCE_FOREIGN);
                    }
                }
            }

            ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
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
        } else if (settings.searchItemIds) {
            builder.add(element.getResourceId(), SearchField.SOURCE_ID);
        }

        if (settings.searchTooltips) {
            int used = 0;
            for (String line : element.getTooltipStrings()) {
                builder.add(line, SearchField.SOURCE_TOOLTIP);
                if (++used >= MAX_TOOLTIP_LINES) {
                    break;
                }
            }
        }
    }
}
