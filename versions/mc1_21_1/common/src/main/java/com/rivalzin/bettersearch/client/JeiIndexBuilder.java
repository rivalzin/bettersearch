package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// JEI hands out its own list order, keep the positions
public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static SearchIndex<IListElement<?>> build(List<IListElement<?>> source,
                                                     IIngredientManager manager,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Item.TooltipContext tooltipContext,
                                                     Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<IListElement<?>>> entries = new ArrayList<>(source.size());
        for (IListElement<?> element : source) {
            try {
                EntryBuilder<IListElement<?>> builder = new EntryBuilder<>(element);
                fill(builder, element, manager, languages, codes, settings, tooltipContext, player,
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

    private static <V> void fill(EntryBuilder<IListElement<?>> builder,
                                 IListElement<V> element,
                                 IIngredientManager manager,
                                 LanguageTable languages,
                                 List<String> codes,
                                 SearchSettings settings,
                                 Item.TooltipContext tooltipContext,
                                 Player player,
                                 boolean englishSearched) {
        ITypedIngredient<V> typed = element.getTypedIngredient();
        V ingredient = typed.getIngredient();

        if (ingredient instanceof ItemStack stack) {
            CreativeIndexBuilder.fill(builder, stack, languages, codes, settings, tooltipContext, player,
                    englishSearched);
            return;
        }

        IIngredientHelper<V> helper = manager.getIngredientHelper(typed.getType());
        builder.add(helper.getDisplayName(ingredient), SearchField.SOURCE_NATIVE);

        ResourceLocation id = helper.getResourceLocation(ingredient);
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
