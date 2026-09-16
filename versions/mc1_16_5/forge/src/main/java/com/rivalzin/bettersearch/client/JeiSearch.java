package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.ingredients.IIngredientListElementInfo;
import mezz.jei.ingredients.IngredientFilter;
import net.minecraft.client.Minecraft;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class JeiSearch {
    private static final String JEI_PREFIXES = "#$^%";

    private static final com.rivalzin.bettersearch.async.SourceSnapshot<IIngredientListElementInfo<?>> SOURCES =
            new com.rivalzin.bettersearch.async.SourceSnapshot<>();
    private static final AsyncIndex<IIngredientListElementInfo<?>> INDEX = new AsyncIndex<>("JEI ingredients");

    private static volatile WeakReference<IngredientFilter> filterRef = new WeakReference<>(null);

    static {

        BetterSearchClient.onInvalidate(JeiSearch::invalidate);
        BetterSearchClient.onSettingsApplied(() -> {
            try {
                IngredientFilter filter = filterRef.get();
                if (filter != null) {
                    filter.invalidateCache();
                }
            } catch (Exception | LinkageError ignored) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
            }
        });
    }

    private JeiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
        SOURCES.clear();
    }

    public static boolean wants(String filterText) {
        SearchSettings settings = BetterSearchClient.settings();
        if (!BetterSearchClient.isEnabled() || !settings.searchJei) {
            return false;
        }
        if (filterText == null || filterText.trim().isEmpty()) {
            return false;
        }
        return !usesJeiSyntax(filterText);
    }

    public static List<IIngredientListElementInfo<?>> search(String filterText,
                                                   List<IIngredientListElementInfo<?>> jeiResult,
                                                   Collection<IIngredientListElementInfo<?>> source,
                                                   IngredientFilter filter, Object sourceIdentity) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchJei) {
                return null;
            }
            if (filterText == null || filterText.trim().isEmpty()) {
                return null;
            }
            if (source == null || source.isEmpty() || usesJeiSyntax(filterText)) {
                return null;
            }
            remember(filter);

            SearchIndex<IIngredientListElementInfo<?>> index = ensureIndex(source, settings, sourceIdentity);
            if (index == null) {
                return null;
            }
            SearchQuery query = SearchQuery.parse(filterText, settings);
            if (query.isEmpty()) {
                return null;
            }

            if ((query.isBrowseOnly() || SearchQuery.isBrowsingByMod(filterText))
                    && jeiResult != null && !jeiResult.isEmpty()) {
                return null;
            }

            List<IIngredientListElementInfo<?>> found = index.search(query, settings);
            List<IIngredientListElementInfo<?>> ours = new ArrayList<>(found.size());
            Set<IIngredientListElementInfo<?>> seen = new HashSet<>(Math.max(16, found.size() * 2));
            for (IIngredientListElementInfo<?> element : found) {
                if (!element.getElement().isVisible()) {
                    continue;
                }

                if (seen.add(element)) {
                    ours.add(element);
                }
            }

            if (jeiResult == null || jeiResult.isEmpty()) {
                return ours.isEmpty() ? null : ours;
            }

            List<IIngredientListElementInfo<?>> merged =
                    new ArrayList<>(ours.size() + jeiResult.size());
            if (settings.sortByRelevance) {
                merged.addAll(ours);
                for (IIngredientListElementInfo<?> typed : jeiResult) {
                    if (seen.add(typed)) {
                        merged.add(typed);
                    }
                }
            } else {
                Set<IIngredientListElementInfo<?>> fromJei = new HashSet<>(jeiResult);
                merged.addAll(jeiResult);
                for (IIngredientListElementInfo<?> typed : ours) {
                    if (!fromJei.contains(typed)) {
                        merged.add(typed);
                    }
                }
            }
            return merged;
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            com.rivalzin.bettersearch.BetterSearch.LOGGER.debug(
                    "[{}] JEI search left untouched: {}",
                    com.rivalzin.bettersearch.BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static void remember(IngredientFilter filter) {
        if (filter != null && filterRef.get() != filter) {
            filterRef = new WeakReference<>(filter);
        }
    }

    private static SearchIndex<IIngredientListElementInfo<?>> ensureIndex(Collection<IIngredientListElementInfo<?>> source,
                                                                SearchSettings settings, Object sourceIdentity) {
        final List<IIngredientListElementInfo<?>> capturedSource = SOURCES.capture(sourceIdentity, source);
        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<IIngredientListElementInfo<?>> ready = INDEX.ready(capturedSource, capturedSource.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        return INDEX.getPrepared(capturedSource, capturedSource.size(), stamp, () -> {
            final List<IIngredientListElementInfo<?>> copy = capturedSource;
            final LanguageTable languages = BetterSearchClient.languages();
            final SearchSettings captured = settings.copy();
            final net.minecraft.world.entity.player.Player player = minecraft.player;
            return JeiIndexBuilder.prepare(copy, languages, captured, player);
        }, JeiSearch::askJeiToSearchAgain);
    }

    private static void askJeiToSearchAgain() {
        IngredientFilter filter = filterRef.get();
        if (filter != null) {
            filter.invalidateCache();
        }
    }

    private static boolean usesJeiSyntax(String filterText) {
        if (filterText.indexOf('|') >= 0) {
            return true;
        }
        for (String piece : filterText.split("\\s+")) {
            if (!piece.isEmpty() && JEI_PREFIXES.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
