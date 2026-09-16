package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ReiIndexBuilder {
    private ReiIndexBuilder() {
    }

    public static java.util.function.Supplier<SearchIndex<EntryStack<?>>> prepare(List<EntryStack<?>> source,
                                                   LanguageTable languages,
                                                   SearchSettings settings,
                                                   Player player) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        return EntrySnapshot.capture(source, stack -> {
            try {
                if (stack == null || stack.isEmpty()) {
                    return null;
                }
                EntrySnapshot<EntryStack<?>> builder = new EntrySnapshot<>(stack);
                Object value = stack.getValue();
                if (value instanceof ItemStack item && !item.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, item, languages, codes, settings,
                            player, englishSearched);
                } else {
                    fillOther(builder, stack, settings);
                }
                if (!builder.isEmpty()) {
                    return builder;
                }
            } catch (RuntimeException | LinkageError t) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
                BetterSearch.LOGGER.debug("[{}] REI entry skipped in index: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
            return null;
        });
    }

    private static void fillOther(EntrySnapshot<EntryStack<?>> builder, EntryStack<?> stack,
                                  SearchSettings settings) {
        builder.add(stack.asFormatStrippedText().getString(), SearchField.SOURCE_NATIVE);

        ResourceLocation id = stack.getIdentifier();
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
