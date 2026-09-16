package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.common.ingredients.IListElementInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class JeiIndexBuilder {
    private JeiIndexBuilder() {
    }

    public static java.util.function.Supplier<SearchIndex<IListElementInfo<?>>> prepare(List<IListElementInfo<?>> source,
                                                     LanguageTable languages,
                                                     SearchSettings settings,
                                                     Player player) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        return EntrySnapshot.capture(source, element -> {
            try {
                EntrySnapshot<IListElementInfo<?>> builder = new EntrySnapshot<>(element);
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

    private static <V> void fill(EntrySnapshot<IListElementInfo<?>> builder,
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
