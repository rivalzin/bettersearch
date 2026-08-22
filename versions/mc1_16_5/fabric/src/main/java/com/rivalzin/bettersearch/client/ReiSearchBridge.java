package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.EntryBuilder;
import com.rivalzin.bettersearch.core.SearchField;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ReiSearchBridge {
    private static final String REI_SYNTAX = "#$";

    private static volatile SearchIndex<EntryStack> index;
    private static volatile int indexedSize = -1;
    private static volatile long indexedStamp = Long.MIN_VALUE;

    private ReiSearchBridge() {
    }

    // REI 5.x on fabric: entries are raw EntryStack, no wrapper type yet
    public static List<EntryStack> search(String query, List<EntryStack> result,
                                          List<EntryStack> source) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei) {
                return null;
            }
            if (query == null || query.trim().isEmpty() || source == null || source.isEmpty()) {
                return null;
            }
            if (usesReiSyntax(query)) {
                return null;
            }

            SearchIndex<EntryStack> ready = buildIndex(source, settings);
            if (ready == null) {
                return null;
            }
            SearchQuery parsed = SearchQuery.parse(query, settings);
            if (parsed.isEmpty()) {
                return null;
            }

            List<EntryStack> ours = ready.search(parsed, settings);
            if (result == null || result.isEmpty()) {
                return ours.isEmpty() ? null : Collections.unmodifiableList(new ArrayList<EntryStack>(ours));
            }

            List<EntryStack> joined = new ArrayList<EntryStack>(ours.size() + result.size());
            if (settings.sortByRelevance) {
                Set<EntryStack> seen = new HashSet<EntryStack>(ours);
                joined.addAll(ours);
                for (EntryStack stack : result) {
                    if (seen.add(stack)) {
                        joined.add(stack);
                    }
                }
            } else {
                Set<EntryStack> fromRei = new HashSet<EntryStack>(result);
                joined.addAll(result);
                for (EntryStack stack : ours) {
                    if (!fromRei.contains(stack)) {
                        joined.add(stack);
                    }
                }
            }
            return Collections.unmodifiableList(joined);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] REI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    // REI gives the list already filtered, so ours is merged in, not replacing
    private static SearchIndex<EntryStack> buildIndex(List<EntryStack> source,
                                                        SearchSettings settings) {
        long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EntryStack> current = index;
        if (current != null && indexedSize == source.size() && indexedStamp == stamp) {
            return current;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        long started = System.nanoTime();
        LanguageTable languages = BetterSearchClient.languages();
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishHit = CreativeIndexBuilder.englishSearched(codes);

        List<SearchIndex.Entry<EntryStack>> entries =
                new ArrayList<SearchIndex.Entry<EntryStack>>(source.size());
        for (EntryStack stack : source) {
            try {
                EntryBuilder<EntryStack> builder = new EntryBuilder<EntryStack>(stack);
                ItemStack item = itemOf(stack);
                if (item != null && !item.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, item, languages, codes, settings,
                            minecraft.player, englishHit);
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

        SearchIndex<EntryStack> built = new SearchIndex<EntryStack>(entries);
        BetterSearch.LOGGER.info("[{}] REI index ready: {} of {} entries in {} ms",
                BetterSearch.MOD_NAME, entries.size(), source.size(),
                (System.nanoTime() - started) / 1000000);
        index = built;
        indexedSize = source.size();
        indexedStamp = stamp;
        return built;
    }

    private static void fillOther(EntryBuilder<EntryStack> builder, EntryStack stack,
                                       SearchSettings settings) {
        builder.add(stack.asFormattedText().getString(), SearchField.SOURCE_NATIVE);

        Optional<ResourceLocation> id = stack.getIdentifier();
        if (id != null && id.isPresent()) {
            ResourceLocation local = id.get();
            builder.modId(local.getNamespace());
            if (settings.searchItemIds) {
                builder.addNormalized(local.getNamespace() + ' '
                        + local.getPath().replace('_', ' '), SearchField.SOURCE_ID);
            }
        }
    }

    private static ItemStack itemOf(EntryStack stack) {
        try {
            return stack.getType() == EntryStack.Type.ITEM ? stack.getItemStack() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean usesReiSyntax(String query) {
        for (String piece : query.split("\\s+")) {
            if (!piece.isEmpty() && REI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
