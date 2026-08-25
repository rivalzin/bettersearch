package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.search.EmiSearch;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EmiSearchBridge {
    private static final String EMI_SYNTAX = "#$/|";

    private static final Object BUILD_LOCK = new Object();

    private static volatile SearchIndex<EmiIngredient> index;
    private static volatile int indexedSize = -1;
    // EMI caches its own list, rebuild when the settings stamp moves
    private static volatile long indexedStamp = Long.MIN_VALUE;

    static {
        BetterSearchClient.onSettingsApplied(() -> {
            try {
                EmiSearch.update();
            } catch (Throwable ignored) {
            }
        });
    }

    private EmiSearchBridge() {
    }

    public static void invalidate() {
        index = null;
        indexedSize = -1;
        indexedStamp = Long.MIN_VALUE;
    }

    public static List<? extends EmiIngredient> search(String query,
                                                       List<? extends EmiIngredient> result,
                                                       List<? extends EmiIngredient> source) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchEmi) {
                return null;
            }
            if (query == null || query.isBlank() || source == null || source.isEmpty()) {
                return null;
            }
            if (usesEmiSyntax(query)) {
                return null;
            }

            SearchIndex<EmiIngredient> ready = ensureIndex(source, settings);
            if (ready == null) {
                return null;
            }
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }

            List<EmiIngredient> ours = ready.search(parsed, settings);
            if (result == null || result.isEmpty()) {
                return ours.isEmpty() ? null : List.copyOf(ours);
            }

            List<EmiIngredient> merged = new ArrayList<>(ours.size() + result.size());
            if (settings.sortByRelevance) {
                Set<EmiIngredient> seen = new HashSet<>(ours);
                merged.addAll(ours);
                for (EmiIngredient ingredient : result) {
                    if (seen.add(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            } else {
                Set<EmiIngredient> fromEmi = new HashSet<>(result);
                merged.addAll(result);
                for (EmiIngredient ingredient : ours) {
                    if (!fromEmi.contains(ingredient)) {
                        merged.add(ingredient);
                    }
                }
            }
            return List.copyOf(merged);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] EMI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static SearchIndex<EmiIngredient> ensureIndex(List<? extends EmiIngredient> source,
                                                          SearchSettings settings) {
        long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EmiIngredient> current = index;
        if (current != null && indexedSize == source.size() && indexedStamp == stamp) {
            return current;
        }
        // EMI starts a thread per keystroke and lets the old ones run on, so without this
        // four of them would build the very same index at the same time
        synchronized (BUILD_LOCK) {
            current = index;
            if (current != null && indexedSize == source.size() && indexedStamp == stamp) {
                return current;
            }
            return buildIndex(source, settings, stamp);
        }
    }

    private static SearchIndex<EmiIngredient> buildIndex(List<? extends EmiIngredient> source,
                                                         SearchSettings settings, long stamp) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        long start = System.nanoTime();
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishSearched = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<EmiIngredient>> entries = new ArrayList<>(source.size());
        for (EmiIngredient ingredient : source) {
            try {
                EntryBuilder<EmiIngredient> builder = new EntryBuilder<>(ingredient);
                ItemStack stack = stackOf(ingredient);
                if (stack != null && !stack.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, stack, languages, codes, settings,
                            minecraft.player, englishSearched);
                } else {
                    fillOther(builder, ingredient, settings);
                }
                if (!builder.isEmpty()) {
                    entries.add(builder.build());
                }
            } catch (Throwable t) {
                BetterSearch.LOGGER.debug("[{}] skipped EMI ingredient: {}",
                        BetterSearch.MOD_NAME, t.toString());
            }
        }

        SearchIndex<EmiIngredient> built = new SearchIndex<>(entries);
        BetterSearch.LOGGER.info("[{}] EMI index ready: {} of {} ingredients in {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - start) / 1_000_000);
        indexedSize = source.size();
        indexedStamp = stamp;
        // published last, so whoever sees this index also sees the size and stamp behind it
        index = built;
        return built;
    }

    private static void fillOther(EntryBuilder<EmiIngredient> builder, EmiIngredient ingredient,
                                  SearchSettings settings) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks.isEmpty()) {
            return;
        }
        EmiStack first = stacks.get(0);
        builder.add(first.getName().getString(), SearchField.SOURCE_NATIVE);

        ResourceLocation id = first.getId();
        if (id != null) {
            builder.modId(id.getNamespace());
            builder.family(id.getPath());
            if (settings.searchItemIds) {
                builder.add(id.getNamespace() + ' ' + id.getPath().replace('_', ' '),
                        SearchField.SOURCE_ID);
            }
        }
    }

    private static ItemStack stackOf(EmiIngredient ingredient) {
        try {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            return stacks.isEmpty() ? null : stacks.get(0).getItemStack();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean usesEmiSyntax(String query) {
        if (query.indexOf('|') >= 0) {
            return true;
        }
        for (String piece : query.split("\\s+")) {
            if (!piece.isEmpty() && EMI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
