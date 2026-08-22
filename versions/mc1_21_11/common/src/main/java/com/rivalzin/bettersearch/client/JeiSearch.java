package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IngredientFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// JEI asks per ingredient, so the query is parsed once and kept
public final class JeiSearch {
    private static final String JEI_PREFIXES = "#$^%";

    private static final AsyncIndex<IListElement<?>> INDEX = new AsyncIndex<>("JEI ingredients");

    private static WeakReference<IngredientFilter> filterRef = new WeakReference<>(null);

    private JeiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    public static List<ITypedIngredient<?>> search(String filterText,
                                                   List<ITypedIngredient<?>> jeiResult,
                                                   Collection<IListElement<?>> source,
                                                   IIngredientManager manager,
                                                   IngredientFilter filter) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchJei) {
                return null;
            }
            if (filterText == null || filterText.isBlank() || manager == null) {
                return null;
            }
            if (source == null || source.isEmpty() || usesJeiSyntax(filterText)) {
                return null;
            }
            remember(filter);

            SearchIndex<IListElement<?>> index = ensureIndex(source, manager, settings);
            if (index == null) {
                return null;
            }
            SearchQuery query = SearchQuery.parse(filterText, settings);
            if (query.isEmpty()) {
                return null;
            }

            List<IListElement<?>> found = index.search(query, settings);
            List<ITypedIngredient<?>> ours = new ArrayList<>(found.size());
            Set<ITypedIngredient<?>> seen = new HashSet<>(Math.max(16, found.size() * 2));
            for (IListElement<?> element : found) {
                if (!element.isVisible()) {
                    continue;
                }
                ITypedIngredient<?> typed = element.getTypedIngredient();
                if (seen.add(typed)) {
                    ours.add(typed);
                }
            }

            if (jeiResult == null || jeiResult.isEmpty()) {
                return ours.isEmpty() ? null : ours;
            }

            List<ITypedIngredient<?>> merged =
                    new ArrayList<>(ours.size() + jeiResult.size());
            if (settings.sortByRelevance) {
                merged.addAll(ours);
                for (ITypedIngredient<?> typed : jeiResult) {
                    if (seen.add(typed)) {
                        merged.add(typed);
                    }
                }
            } else {
                Set<ITypedIngredient<?>> fromJei = new HashSet<>(jeiResult);
                merged.addAll(jeiResult);
                for (ITypedIngredient<?> typed : ours) {
                    if (!fromJei.contains(typed)) {
                        merged.add(typed);
                    }
                }
            }
            return merged;
        } catch (Throwable t) {
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

    private static SearchIndex<IListElement<?>> ensureIndex(Collection<IListElement<?>> source,
                                                            IIngredientManager manager,
                                                            SearchSettings settings) {
        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<IListElement<?>> ready = INDEX.ready(manager, source.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        final List<IListElement<?>> copy = List.copyOf(source);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings captured = settings.copy();
        final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        final net.minecraft.world.entity.player.Player player = minecraft.player;

        return INDEX.get(manager, copy.size(), stamp,
                () -> JeiIndexBuilder.build(copy, manager, languages, captured, tooltipContext, player),
                JeiSearch::askJeiToSearchAgain);
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
