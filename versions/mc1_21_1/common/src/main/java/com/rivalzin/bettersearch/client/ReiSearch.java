package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.search.SearchFilter;
import me.shedaniel.rei.impl.client.search.AsyncSearchManager;
import me.shedaniel.rei.api.common.entry.EntryStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReiSearch {
    private static final String REI_SYNTAX = "#$*-";

    private static final com.rivalzin.bettersearch.async.SourceSnapshot<EntryStack<?>> SOURCES =
            new com.rivalzin.bettersearch.async.SourceSnapshot<>();
    private static final AsyncIndex<EntryStack<?>> INDEX = new AsyncIndex<>("REI entries");

    private static volatile java.lang.ref.WeakReference<AsyncSearchManager> managerRef =
            new java.lang.ref.WeakReference<>(null);

    private static final java.util.concurrent.atomic.AtomicInteger generation =
            new java.util.concurrent.atomic.AtomicInteger();

    static {

        BetterSearchClient.onInvalidate(ReiSearch::invalidate);
        BetterSearchClient.onSettingsApplied(ReiSearch::onSettingsChanged);
    }

    private static void onSettingsChanged() {

        generation.incrementAndGet();
        markDirty();
    }

    private static void markDirty() {
        try {
            AsyncSearchManager manager = managerRef.get();
            if (manager != null) {

                manager.getClass().getMethod("markDirty").invoke(manager);
            }
        } catch (Exception | LinkageError ignored) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
        }
        try {

            me.shedaniel.rei.api.client.REIRuntime.getInstance().getOverlay()
                    .ifPresent(overlay -> overlay.queueReloadSearch());
        } catch (Exception | LinkageError ignored) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
        }
    }

    private static void onIndexReady() {
        generation.incrementAndGet();
        markDirty();
    }

    public static void rememberManager(AsyncSearchManager manager) {
        if (manager != null && managerRef.get() != manager) {
            managerRef = new java.lang.ref.WeakReference<>(manager);
        }
    }

    private ReiSearch() {
    }

    public static void invalidate() {
        INDEX.invalidate();
        SOURCES.clear();
        generation.incrementAndGet();
    }

    public static Map<EntryStack<?>, Integer> rankingOf(SearchFilter filter) {
        return filter instanceof BetterSearchFilter ours ? ours.positions() : null;
    }

    public static SearchFilter wrap(SearchFilter original) {
        try {
            if (original == null) {
                return null;
            }
            String text = original.getFilter();
            if (text == null || text.isBlank() || usesReiSyntax(text)) {
                return original;
            }

            if (SearchQuery.isBrowsingByMod(text)) {
                return original;
            }
            return new BetterSearchFilter(original, text);
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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
        private volatile Match matched;

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

        Map<EntryStack<?>, Integer> positions() {
            return ours();
        }

        private Map<EntryStack<?>, Integer> ours() {
            int now = generation.get();
            Match current = matched;
            if (current != null && current.generation == now) {
                return current.positions;
            }
            synchronized (this) {
                now = generation.get();
                current = matched;
                if (current == null || current.generation != now) {
                    current = new Match(run(text), now);
                    if (generation.get() != now) {
                        return java.util.Collections.emptyMap();
                    }
                    matched = current;
                }
                return current.positions;
            }
        }

        private static final class Match {
            final Map<EntryStack<?>, Integer> positions;
            final int generation;

            Match(Map<EntryStack<?>, Integer> positions, int generation) {
                this.positions = positions;
                this.generation = generation;
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

            if (!BetterSearchClient.isEnabled() || !settings.searchRei) {
                return Map.of();
            }
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
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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

        final List<EntryStack<?>> capturedSource = SOURCES.capture(source);

        final long stamp = BetterSearchClient.languageStamp();
        SearchIndex<EntryStack<?>> ready = INDEX.ready(capturedSource, capturedSource.size(), stamp);
        if (ready != null) {
            return ready;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }

        return INDEX.getPrepared(capturedSource, capturedSource.size(), stamp, () -> {
            final List<EntryStack<?>> copy = capturedSource;
            final LanguageTable languages = BetterSearchClient.languages();
            final SearchSettings captured = settings.copy();
            final Item.TooltipContext tooltipContext = Item.TooltipContext.of(minecraft.level);
            final net.minecraft.world.entity.player.Player player = minecraft.player;
            return ReiIndexBuilder.prepare(copy, languages, captured, tooltipContext, player);
        }, ReiSearch::onIndexReady);
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

            final String texto = filter == null ? null : filter.getFilter();
            if (texto != null && (SearchQuery.isBrowsingByMod(texto)
                    || SearchQuery.parse(texto, settings).isBrowseOnly())) {
                return creativeLayout(ordered, unwrap);
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

            long[] keyed = new long[ours.size()];
            for (int i = 0; i < ours.size(); i++) {
                Integer at = positions.get(unwrap.apply(ours.get(i)));
                keyed[i] = ((long) (at == null ? Integer.MAX_VALUE : at) << 32) | i;
            }
            java.util.Arrays.sort(keyed);
            List<T> sorted = new ArrayList<>(ours.size());
            for (long key : keyed) {
                sorted.add(ours.get((int) (key & 0xFFFFFFFFL)));
            }
            ours = sorted;
            ours.addAll(rest);
            return ours;
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);

            BetterSearch.LOGGER.debug("[{}] REI order unchanged: {}",
                    BetterSearch.MOD_NAME, t.toString());
            return null;
        }
    }

    private static <T> List<T> creativeLayout(List<T> ordered,
                                              java.util.function.Function<T, EntryStack<?>> unwrap) {
        java.util.Map<net.minecraft.world.item.Item, Integer> order =
                BetterSearchClient.creativeOrder();
        if (order.isEmpty()) {
            relatarUmaVez(0, 0);
            return null;
        }
        final int size = ordered.size();

        long[] keyed = new long[size];
        boolean anyKnown = false;
        int colocados = 0;
        for (int i = 0; i < size; i++) {
            EntryStack<?> stack = unwrap.apply(ordered.get(i));
            int place = Integer.MAX_VALUE;
            if (stack != null) {
                Object value = stack.getValue();
                if (value instanceof net.minecraft.world.item.ItemStack) {
                    Integer at = order.get(((net.minecraft.world.item.ItemStack) value).getItem());
                    if (at != null) {
                        place = at;
                        anyKnown = true;
                        colocados++;
                    }
                }
            }
            keyed[i] = ((long) place << 32) | (long) i;
        }
        relatarUmaVez(size, colocados);
        if (!anyKnown) {
            return null;
        }
        java.util.Arrays.sort(keyed);
        List<T> out = new ArrayList<>(size);
        for (long key : keyed) {
            out.add(ordered.get((int) (key & 0xFFFFFFFFL)));
        }
        return out;
    }

    private static boolean relatou;

    private static void relatarUmaVez(int total, int colocados) {
        if (relatou) {
            return;
        }
        relatou = true;
        BetterSearch.LOGGER.info("[{}] REI layout: {} entries, {} placed by creative order,"
                        + " {} left in REI order (creative order has {} items)",
                BetterSearch.MOD_NAME, total, colocados, total - colocados,
                BetterSearchClient.creativeOrder().size());
    }
}
