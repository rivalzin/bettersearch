package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.async.EntrySnapshot;
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

    private static volatile java.lang.ref.WeakReference<me.shedaniel.rei.gui.widget.EntryListWidget> listRef =
            new java.lang.ref.WeakReference<>(null);
    private static volatile String lastQuery = "";

    static {
        BetterSearchClient.onSettingsApplied(ReiSearchBridge::searchAgain);
        BetterSearchClient.onInvalidate(ReiSearchBridge::invalidate);
    }

    public static void remember(me.shedaniel.rei.gui.widget.EntryListWidget list, String query) {
        if (list != null && listRef.get() != list) {
            listRef = new java.lang.ref.WeakReference<>(list);
        }
        lastQuery = query == null ? "" : query;
    }

    private static void searchAgain() {
        try {
            me.shedaniel.rei.gui.widget.EntryListWidget list = listRef.get();
            if (list != null) {
                list.updateSearch(lastQuery, true);
            }
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            BetterSearch.LOGGER.debug("[{}] REI list left as it was: {}",
                    BetterSearch.MOD_NAME, t.toString());
        }
    }

    private static final com.rivalzin.bettersearch.async.SourceSnapshot<EntryStack> SOURCES =
            new com.rivalzin.bettersearch.async.SourceSnapshot<>();
    private static final AsyncIndex<EntryStack> INDEX = new AsyncIndex<>("REI entries");

    public static void invalidate() {
        INDEX.invalidate();
        SOURCES.clear();
    }

    private ReiSearchBridge() {
    }

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

            if ((parsed.isBrowseOnly() || SearchQuery.isBrowsingByMod(query))
                    && result != null && !result.isEmpty()) {
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
                    if (fromRei.add(stack)) {
                        joined.add(stack);
                    }
                }
            }
            return Collections.unmodifiableList(joined);
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            BetterSearch.LOGGER.debug("[{}] REI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static SearchIndex<EntryStack> buildIndex(List<EntryStack> source,
                                                        SearchSettings settings) {
        long stamp = BetterSearchClient.languageStamp();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        List<EntryStack> capturedSource = SOURCES.capture(source);
        return INDEX.getPrepared(capturedSource, capturedSource.size(), stamp, () -> prepare(
                capturedSource, settings.copy(), BetterSearchClient.languages(), minecraft.player),
                ReiSearchBridge::searchAgain);
    }

    private static java.util.function.Supplier<SearchIndex<EntryStack>> prepare(List<EntryStack> source,
            SearchSettings settings, LanguageTable languages, net.minecraft.world.entity.player.Player player) {
        List<String> codes = CreativeIndexBuilder.activeCodes(languages, settings);
        boolean englishHit = CreativeIndexBuilder.englishSearched(codes);
        return EntrySnapshot.capture(source, stack -> {
            try {
                EntrySnapshot<EntryStack> builder = new EntrySnapshot<>(stack);
                ItemStack item = itemOf(stack);
                if (item != null && !item.isEmpty()) {
                    CreativeIndexBuilder.fill(builder, item, languages, codes, settings,
                            player, englishHit);
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

    private static void fillOther(EntrySnapshot<EntryStack> builder, EntryStack stack,
                                       SearchSettings settings) {
        builder.add(stack.asFormattedText().getString(), SearchField.SOURCE_NATIVE);

        Optional<ResourceLocation> id = stack.getIdentifier();
        if (id != null && id.isPresent()) {
            ResourceLocation local = id.get();
            builder.modId(local.getNamespace());
            builder.family(local.getPath());
            if (settings.searchItemIds) {
                builder.add(local.getNamespace() + ' '
                        + local.getPath().replace('_', ' '), SearchField.SOURCE_ID);
            }
        }
    }

    private static ItemStack itemOf(EntryStack stack) {
        try {
            return stack.getType() == EntryStack.Type.ITEM ? stack.getItemStack() : null;
        } catch (RuntimeException | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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
