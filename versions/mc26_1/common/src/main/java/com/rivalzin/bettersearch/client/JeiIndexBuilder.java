package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static java.util.function.Supplier<SearchIndex<IListElement<?>>> prepare(List<IListElement<?>> source,
                                                     IIngredientManager manager,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Item.TooltipContext tooltipContext,
                                                     Player player) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        return EntrySnapshot.capture(source, element -> {
            try {
                EntrySnapshot<IListElement<?>> builder = new EntrySnapshot<>(element);
                fill(builder, element, manager, languages, codes, settings, tooltipContext, player,
                        englishSearched);
                if (!builder.isEmpty()) {
                    return builder;
                }
            } catch (RuntimeException | LinkageError t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.debug("[{}] skipped JEI ingredient: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
            return null;
        });
    }

    private static <V> void fill(EntrySnapshot<IListElement<?>> builder,
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

        Identifier id = helper.getIdentifier(ingredient);
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
