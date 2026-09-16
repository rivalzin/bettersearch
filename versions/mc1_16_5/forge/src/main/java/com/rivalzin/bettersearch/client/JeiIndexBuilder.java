package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.ingredients.IIngredientListElementInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static java.util.function.Supplier<SearchIndex<IIngredientListElementInfo<?>>> prepare(List<IIngredientListElementInfo<?>> source,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Player player) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        return EntrySnapshot.capture(source, element -> {
            try {
                EntrySnapshot<IIngredientListElementInfo<?>> builder = new EntrySnapshot<>(element);
                fill(builder, element, languages, codes, settings, player,
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

    private static <V> void fill(EntrySnapshot<IIngredientListElementInfo<?>> builder,
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
            builder.family(path);
            if (settings.searchItemIds) {
                String text = ns.isEmpty() ? path.replace('_', ' ')
                        : ns + ' ' + path.replace('_', ' ');
                builder.add(text, SearchField.SOURCE_ID);
            }
        }
    }
}
