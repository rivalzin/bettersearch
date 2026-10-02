package com.rivalzin.bettersearch.forge.jei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.forge.IntegrationRetry;
import com.rivalzin.bettersearch.forge.nei.NeiIntegration;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.client.LangTable;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public final class JeiSearchBridge {
    private static final int MAX_TOOLTIP_LINES = 6;
    private final AsyncIndexState<SearchIndex<Integer>> index = new AsyncIndexState<>(
            task -> Minecraft.getMinecraft().addScheduledTask(task), ForkJoinPool.commonPool(),
            task -> Minecraft.getMinecraft().addScheduledTask(task),
            this::reportFailure);
    private static volatile int generation;
    private final Elements source;
    private final String viewer;
    private final IntegrationRetry retry = new IntegrationRetry();
    private Context context;
    private volatile Cache cache;

    interface Elements {
        List<?> get() throws Exception;
    }

    JeiSearchBridge(Elements source) {
        this(source, "jei");
    }

    JeiSearchBridge(Elements source, String viewer) {
        this.source = source;
        this.viewer = viewer;
    }

    synchronized void invalidate() {
        index.invalidate();
        context = null;
        cache = null;
    }

    private void reportFailure(Throwable error) {
        if (retry.failed(error, System.nanoTime())) {
            BetterSearch.LOGGER.warn("[{}] JEI/HEI search unavailable, using native search: {}",
                    BetterSearch.MOD_NAME, IntegrationRetry.cause(error).toString());
        }
    }

    static int generation() {
        return generation;
    }

    synchronized int[] search(String word) {
        SearchSettings settings = ModConfig.settings();
        if (word == null || word.isEmpty() || !settings.enabled || !enabledViewer(viewer, settings)
                || !retry.ready(System.nanoTime())) {
            return null;
        }
        try {
            SearchIndex<Integer> current = ensureIndex(settings);
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
            cache = new Cache(current, context.elements, word, result);
            return result;
        } catch (Exception | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            reportFailure(error);
            return null;
        }
    }

    static boolean enabledViewer(String viewer, SearchSettings settings) {
        if (NeiIntegration.usesJeiSearch()) {
            return settings.searchNei;
        }
        return "hei".equals(viewer) ? settings.searchHei : settings.searchJei;
    }

    synchronized List<Object> searchElements(String word) {
        int[] positions = search(word);
        List<Object> found = new ArrayList<>();
        Cache cached = cache;
        if (positions == null || cached == null || cached.result != positions) {
            return found;
        }
        for (int position : positions) {
            found.add(cached.elements.get(position));
        }
        return found;
    }

    private synchronized SearchIndex<Integer> ensureIndex(SearchSettings settings) throws Exception {
        LangTable.ensure(settings);
        List<?> list = source.get();
        if (list == null) {
            return null;
        }
        String language = Minecraft.getMinecraft().gameSettings.language;
        int configStamp = ModConfig.stamp();
        int languageStamp = LangTable.stamp();
        if (context == null || context.elements != list || context.configStamp != configStamp
                || context.languageStamp != languageStamp || !context.language.equals(language)) {
            context = new Context(list, language, configStamp, languageStamp);
            index.invalidate();
            cache = null;
        }
        Context key = context;
        return index.getPrepared(key, list.size(), 0L, () -> {
            List<String> languages = settings.crossLanguage ? LangTable.activeCodes(settings) : Collections.emptyList();
            List<?> elements = new ArrayList<>(list);
            List<Integer> positions = new ArrayList<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                positions.add(i);
            }
            return EntrySnapshot.capture(positions, position -> {
                try {
                    IIngredientListElement<?> element = (IIngredientListElement<?>) elements.get(position);
                    EntrySnapshot<Integer> captured = new EntrySnapshot<>(position);
                    fill(captured, element, settings, languages);
                    return captured;
                } catch (Exception | LinkageError error) {
                    FailurePolicy.rethrowFatal(error);
                    BetterSearch.LOGGER.debug("[{}] skipped JEI ingredient: {}", BetterSearch.MOD_NAME, error.toString());
                    return null;
                }
            });
        }, () -> {
            retry.succeeded();
            generation++;
        });
    }

    private static final class Context {
        final List<?> elements;
        final String language;
        final int configStamp;
        final int languageStamp;

        Context(List<?> elements, String language, int configStamp, int languageStamp) {
            this.elements = elements;
            this.language = language;
            this.configStamp = configStamp;
            this.languageStamp = languageStamp;
        }
    }

    private static final class Cache {
        final SearchIndex<Integer> index;
        final List<?> elements;
        final String word;
        final int[] result;

        Cache(SearchIndex<Integer> index, List<?> elements, String word, int[] result) {
            this.index = index;
            this.elements = elements;
            this.word = word;
            this.result = result;
        }
    }

    private static void fill(EntrySnapshot<Integer> builder, IIngredientListElement<?> element,
                                  SearchSettings settings, List<String> languages) {
        builder.add(element.getDisplayName(), SearchField.SOURCE_NATIVE);

        Object ingredient = element.getIngredient();
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;

            if (settings.crossLanguage) {
                String key = stack.getTranslationKey() + ".name";
                for (String code : languages) {
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
