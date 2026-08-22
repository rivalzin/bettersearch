package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.ingredients.IIngredientListElementInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// JEI hands out its own list order, keep the positions
public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static SearchIndex<IIngredientListElementInfo<?>> build(List<IIngredientListElementInfo<?>> source,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<IIngredientListElementInfo<?>>> entries = new ArrayList<>(source.size());
        for (IIngredientListElementInfo<?> element : source) {
            try {
                EntryBuilder<IIngredientListElementInfo<?>> builder = new EntryBuilder<>(element);
                fill(builder, element, languages, codes, settings, player,
                        englishSearched);
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped JEI ingredient: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        BetterSearch.LOGGER.info("[{}] JEI index ready: {} of {} ingredients in {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new SearchIndex<>(entries);
    }

    private static <V> void fill(EntryBuilder<IIngredientListElementInfo<?>> builder,
                                 IIngredientListElementInfo<V> element,
                                 LanguageTable languages,
                                 List<String> codes,
                                 SearchSettings settings,
                                 Player player,
                                 boolean englishSearched) {
        V ingredient = element.getElement().getIngredient();

        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            CreativeIndexBuilder.fill(builder, stack, languages, codes, settings, player,
                    englishSearched);
            return;
        }

        builder.add(element.getName(), SearchField.SOURCE_NATIVE);

        String id = element.getResourceId();
        if (id != null && !id.isEmpty()) {
            int colon = id.indexOf(':');
            String ns = colon > 0 ? id.substring(0, colon) : "";
            String path = colon >= 0 ? id.substring(colon + 1) : id;
            if (!ns.isEmpty()) {
                builder.modId(ns);
            }
            if (settings.searchItemIds) {
                String text = ns.isEmpty() ? path.replace('_', ' ')
                        : ns + ' ' + path.replace('_', ' ');
                builder.addNormalized(text, SearchField.SOURCE_ID);
            }
        }
    }
}
