package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.common.ingredients.IListElementInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// JEI hands out its own list order, keep the positions
public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static SearchIndex<IListElementInfo<?>> build(List<IListElementInfo<?>> source,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<IListElementInfo<?>>> entries = new ArrayList<>(source.size());
        for (IListElementInfo<?> element : source) {
            try {
                EntryBuilder<IListElementInfo<?>> builder = new EntryBuilder<>(element);
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

    private static <V> void fill(EntryBuilder<IListElementInfo<?>> builder,
                                 IListElementInfo<V> element,
                                 LanguageTable languages,
                                 List<String> codes,
                                 SearchSettings settings,
                                 Player player,
                                 boolean englishSearched) {
        V ingredient = element.getTypedIngredient().getIngredient();

        if (ingredient instanceof ItemStack stack) {
            CreativeIndexBuilder.fill(builder, stack, languages, codes, settings, player,
                    englishSearched);
            return;
        }

        builder.add(element.getName(), SearchField.SOURCE_NATIVE);

        ResourceLocation id = element.getResourceLocation();
        if (id != null) {
            builder.modId(id.getNamespace());
            builder.family(id.getPath());
            if (settings.searchItemIds) {
                builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
            }
        }
    }
}
