package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.client.LangTable;
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
import java.util.concurrent.ForkJoinPool;

public final class JeiSearchBridge {
    private static final int MAX_TOOLTIP_LINES = 6;
    private static final AsyncIndexState<SearchIndex<Integer>> INDEX = new AsyncIndexState<>(
            task -> Minecraft.getMinecraft().addScheduledTask(task), ForkJoinPool.commonPool(),
            task -> Minecraft.getMinecraft().addScheduledTask(task),
            error -> BetterSearch.LOGGER.error("[{}] JEI index failed, falling back", BetterSearch.MOD_NAME, error));
    private static volatile int generation;
    private static Field itemListField;
    private static Context context;
    private static volatile Cache cache;

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
            SearchIndex<Integer> current = ensureIndex(filter, settings);
            if (current == null) {
                return null;
            }
            Cache cached = cache;
            if (cached != null && current == cached.index && word.equals(cached.word)) {
                return cached.result;
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
            cache = new Cache(current, word, result);
            return result;
        } catch (Exception | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.error("[{}] JEI search failed, falling back", BetterSearch.MOD_NAME, error);
            return null;
        }
    }

    private static synchronized SearchIndex<Integer> ensureIndex(IngredientFilter filter,
                                                                  SearchSettings settings) throws Exception {
        LangTable.ensure(settings);
        if (itemListField == null) {
            itemListField = IngredientFilter.class.getDeclaredField("elementList");
            itemListField.setAccessible(true);
        }
        List<?> list = (List<?>) itemListField.get(filter);
        if (list == null) {
            return null;
        }
        String language = Minecraft.getMinecraft().gameSettings.language;
        int configStamp = ModConfig.stamp();
        int languageStamp = LangTable.stamp();
        if (context == null || context.filter != filter || context.configStamp != configStamp
                || context.languageStamp != languageStamp || !context.language.equals(language)) {
            context = new Context(filter, language, configStamp, languageStamp);
            INDEX.invalidate();
            cache = null;
        }
        Context key = context;
        return INDEX.getPrepared(key, list.size(), 0L, () -> {
            List<?> elements = new ArrayList<>(list);
            List<Integer> positions = new ArrayList<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                positions.add(i);
            }
            return EntrySnapshot.capture(positions, position -> {
                try {
                    IIngredientListElement<?> element = (IIngredientListElement<?>) elements.get(position);
                    EntrySnapshot<Integer> captured = new EntrySnapshot<>(position);
                    fill(captured, element, settings);
                    return captured;
                } catch (Exception | LinkageError error) {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] skipped JEI ingredient: {}", BetterSearch.MOD_NAME, error.toString());
                    return null;
                }
            });
        }, () -> generation++);
    }

    private static final class Context {
        final IngredientFilter filter;
        final String language;
        final int configStamp;
        final int languageStamp;

        Context(IngredientFilter filter, String language, int configStamp, int languageStamp) {
            this.filter = filter;
            this.language = language;
            this.configStamp = configStamp;
            this.languageStamp = languageStamp;
        }
    }

    private static final class Cache {
        final SearchIndex<Integer> index;
        final String word;
        final int[] result;

        Cache(SearchIndex<Integer> index, String word, int[] result) {
            this.index = index;
            this.word = word;
            this.result = result;
        }
    }

    private static void fill(EntrySnapshot<Integer> builder, IIngredientListElement<?> element,
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
