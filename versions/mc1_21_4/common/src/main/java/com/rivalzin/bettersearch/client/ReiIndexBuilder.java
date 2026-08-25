package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// one entry per stack, REI groups them again on its side
public final class ReiIndexBuilder {
    private ReiIndexBuilder() {
    }

    public static SearchIndex<EntryStack<?>> build(List<EntryStack<?>> source,
                                                   LanguageTable languages,
                                                   SearchSettings settings,
                                                   Item.TooltipContext tooltipContext,
                                                   Player player) {
        long start = System.nanoTime();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<EntryStack<?>>> entries = new ArrayList<>(source.size());
        for (EntryStack<?> stack : source) {
            try {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                EntryBuilder<EntryStack<?>> builder = new EntryBuilder<>(stack);
                Object value = stack.getValue();
                if (value instanceof ItemStack item && !item.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, item, languages, codes, settings,
                            tooltipContext, player, englishSearched);
                } else {
                    fillOther(builder, stack, settings);
                }
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] REI entry skipped in index: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        BetterSearch.LOGGER.info("[{}] REI index ready: {} of {} entries in {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new SearchIndex<>(entries);
    }

    private static void fillOther(EntryBuilder<EntryStack<?>> builder, EntryStack<?> stack,
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
