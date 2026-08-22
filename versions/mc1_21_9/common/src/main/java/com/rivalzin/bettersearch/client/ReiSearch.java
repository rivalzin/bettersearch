package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// REI filters first and asks us after, so ours is a merge
public final class ReiSearch {
    private static final String REI_SYNTAX = "#$*-";

    private static final AsyncIndex<EntryStack<?>> INDEX = new AsyncIndex<>("REI entries");

    private ReiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
    }

    public static Map<EntryStack<?>, Integer> rankingOf(SearchFilter filter) {
        return filter instanceof BetterSearchFilter ours ? ours.positionsIfReady() : null;
    }

    public static SearchFilter wrap(SearchFilter original) {
        try {
            if (original == null) {
                return null;
            }
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei) {
                return original;
            }
            String text = original.getFilter();
            if (text == null || text.isBlank() || usesReiSyntax(text)) {
                return original;
            }
            return new BetterSearchFilter(original, text);
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] REI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return original;
        }
    }

    private static boolean usesReiSyntax(String text) {
        if (text.indexOf('|') >= 0 || text.indexOf('"') >= 0 || text.indexOf('/') >= 0) {
            return true;
        }
        for (String piece : text.split("\\s+")) {
            if (!piece.isEmpty() && REI_SYNTAX.indexOf(piece.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static final class BetterSearchFilter implements SearchFilter {
        private final SearchFilter original;
        private final String text;
        private volatile Map<EntryStack<?>, Integer> matched;

        BetterSearchFilter(SearchFilter original, String text) {
            this.original = original;
            this.text = text;
        }

        @Override
        public String getFilter() {
            return original.getFilter();
        }

        @Override
        public void prepareFilter(Collection<EntryStack<?>> stacks) {
            original.prepareFilter(stacks);
        }

        @Override
        public boolean test(EntryStack<?> stack, long hash) {
            return original.test(stack, hash) || ours().containsKey(stack);
        }

        @Override
        public boolean test(EntryStack<?> stack) {
            return original.test(stack) || ours().containsKey(stack);
        }

        Map<EntryStack<?>, Integer> positionsIfReady() {
            return matched;
        }

        private Map<EntryStack<?>, Integer> ours() {
            Map<EntryStack<?>, Integer> ready = matched;
            if (ready != null) {
                return ready;
            }
            synchronized (this) {
                if (matched != null) {
                    return matched;
                }
                matched = run(text);
                return matched;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof BetterSearchFilter wrapped) {
                return original.equals(wrapped.original);
            }
            return original.equals(other);
        }

        @Override
        public int hashCode() {
            return original.hashCode();
        }

        @Override
        public String toString() {
            return "BetterSearch(" + original + ")";
        }
    }

    private static Map<EntryStack<?>, Integer> run(String text) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            SearchIndex<EntryStack<?>> index = ensureIndex(settings);
            if (index == null) {
                return Map.of();
            }
            SearchQuery query = SearchQuery.parse(text, settings);
            if (query.isEmpty()) {
                return Map.of();
            }

            List<EntryStack<?>> found = index.search(query, settings);
            if (found.isEmpty()) {
                return Map.of();
            }

            Map<EntryStack<?>, Integer> positions = new HashMap<>(found.size() * 2);
            for (int i = 0; i < found.size(); i++) {
                positions.putIfAbsent(found.get(i), i);
            }
            return positions;
        } catch (Throwable t) {
            BetterSearch.LOGGER.debug("[{}] REI search left untouched: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return Map.of();
        }
    }

    private static SearchIndex<EntryStack<?>> ensureIndex(SearchSettings settings) {
        EntryRegistry registry = EntryRegistry.getInstance();
        if (registry == null || registry.isReloading()) {
            return null;
        }
        List<EntryStack<?>> source = registry.getPreFilteredList();
        if (source == null || source.isEmpty()) {
            return null;
        }

        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EntryStack<?>> ready = INDEX.ready(registry, source.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        final List<EntryStack<?>> copy = List.copyOf(source);
        final LanguageTable languages = BetterSearchClient.languages();
        final SearchSettings captured = settings.copy();
        final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
        final net.minecraft.world.entity.player.Player player = minecraft.player;

        return INDEX.get(registry, copy.size(), stamp,
                () -> ReiIndexBuilder.build(copy, languages, captured, tooltipContext, player));
    }

    public static <T> List<T> reorder(SearchFilter filter, List<T> ordered,
                                      java.util.function.Function<T, EntryStack<?>> unwrap) {
        try {
            SearchSettings settings = BetterSearchClient.settings();
            if (!BetterSearchClient.isEnabled() || !settings.searchRei || !settings.sortByRelevance) {
                return null;
            }
            if (ordered == null || ordered.size() < 2) {
                return null;
            }
            final Map<EntryStack<?>, Integer> positions = rankingOf(filter);
            if (positions == null || positions.isEmpty()) {
                return null;
            }

            List<T> ours = new ArrayList<>(Math.min(ordered.size(), positions.size()));
            List<T> rest = new ArrayList<>();
            for (T item : ordered) {
                EntryStack<?> stack = unwrap.apply(item);
                if (stack != null && positions.containsKey(stack)) {
                    ours.add(item);
                } else {
                    rest.add(item);
                }
            }
            if (ours.isEmpty()) {
                return null;
            }
            ours.sort(java.util.Comparator.comparingInt(
                    item -> positions.getOrDefault(unwrap.apply(item), Integer.MAX_VALUE)));
            ours.addAll(rest);
            return ours;
        } catch (Throwable t) {
            // same order back = REI had nothing to add, keep its list
            BetterSearch.LOGGER.debug("[{}] REI order unchanged: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }
}
