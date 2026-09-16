package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import com.rivalzin.bettersearch.state.VersionedState;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class BetterSearchClient {
    private static volatile SearchSettings settings = new SearchSettings();
    private static final VersionedState<LanguageTable> languageState = new VersionedState<>(LanguageTable.EMPTY);
    private static final AsyncIndex<ItemStack> index = new AsyncIndex<>("creative");
    private static final AtomicLong languageStamp = new AtomicLong();
    private static final CopyOnWriteArrayList<Runnable> settingsAppliedListeners = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> invalidateListeners = new CopyOnWriteArrayList<>();
    private static volatile Path configFile;
    private static volatile boolean resourcesReady;
    private static volatile long resourceReloadRevision;
    private static volatile long languageFailedAt;
    private static volatile SearchCache queryCache;
    private static final java.util.concurrent.atomic.AtomicBoolean creativeOrderRequested =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile Map<Item, Integer> creativeOrder = Collections.emptyMap();
    private static Object creativeOrderSource;
    private static int creativeOrderSize = -1;
    private static long creativeOrderFingerprint;

    private BetterSearchClient() {
    }

    public static SearchSettings settings() {
        return settings.copy();
    }

    public static LanguageTable languages() {
        ensureLanguagesLoaded();
        return languageState.value();
    }

    public static long languageStamp() {
        return languageStamp.get();
    }

    public static synchronized void setSettings(SearchSettings updated) {
        SearchSettings incoming = updated.copy();
        incoming.sanitize();
        SearchSettings previous = settings;
        if (incoming.equals(previous)) {
            return;
        }
        if (incoming.affectsLanguageTable(previous)) {
            languageState.invalidate();
            languageFailedAt = 0;
        }
        settings = incoming;
        clearQueryCache();
        if (incoming.affectsIndex(previous) || incoming.enabled != previous.enabled) {
            invalidate();
        }
        reloadLanguagesIfNeeded();
    }

    private static synchronized LanguageLoad beginLanguageLoad() {
        return new LanguageLoad(languageState.begin(), settings.copy(), null);
    }

    public static LanguageLoad loadLanguages(ResourceManager resources) {
        LanguageLoad load;
        synchronized (BetterSearchClient.class) {
            resourcesReady = false;
            languageState.reset(LanguageTable.EMPTY);
            load = beginLanguageLoad();
            resourceReloadRevision = load.revision;
        }
        try {
            return new LanguageLoad(load.revision, load.settings, LanguageTable.load(resources, load.settings));
        } catch (RuntimeException error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            BetterSearch.LOGGER.error("[{}] failed to read languages", BetterSearch.MOD_NAME, error);
            return new LanguageLoad(load.revision, load.settings, LanguageTable.EMPTY);
        }
    }

    private static synchronized void reloadLanguagesIfNeeded() {
        if (resourceReloadRevision != 0 || !resourcesReady || languageState.pending() || languageState.value().matchesRequest(settings)
                || (languageFailedAt != 0 && System.nanoTime() - languageFailedAt < 1_000_000_000L)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        LanguageLoad load = beginLanguageLoad();
        try {
            ResourceManager resources = minecraft.getResourceManager();
            CompletableFuture.supplyAsync(() -> LanguageTable.load(resources, load.settings), Util.backgroundExecutor())
                    .whenComplete((table, error) -> {
                        try {
                            minecraft.execute(() -> {
                                if (error != null) {
                                    languageLoadFailed(load.revision, error);
                                } else {
                                    onLanguagesLoaded(new LanguageLoad(load.revision, load.settings, table));
                                }
                            });
                        } catch (RuntimeException | LinkageError schedulingFailure) {
                            languageLoadFailed(load.revision, schedulingFailure);
                        }
                    });
        } catch (RuntimeException | LinkageError schedulingFailure) {
            languageLoadFailed(load.revision, schedulingFailure);
        }
    }

    private static synchronized void languageLoadFailed(long revision, Throwable error) {
        com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
        if (languageState.fail(revision)) {
            languageFailedAt = System.nanoTime();
            BetterSearch.LOGGER.error("[{}] failed to reload languages", BetterSearch.MOD_NAME, error);
        }
    }

    public static synchronized void onLanguagesLoaded(LanguageLoad load) {
        if (load.revision == resourceReloadRevision) {
            resourceReloadRevision = 0;
        } else if (resourceReloadRevision != 0) {
            return;
        }
        resourcesReady = true;
        if (load.table == null || !load.table.matchesRequest(settings)) {
            if (languageState.fail(load.revision)) {
                languageFailedAt = System.nanoTime();
            }
            reloadLanguagesIfNeeded();
            return;
        }
        if (!languageState.publish(load.revision, load.table)) {
            reloadLanguagesIfNeeded();
            return;
        }
        languageFailedAt = 0;
        invalidate();
        notifySettingsApplied();
    }

    public static synchronized void onLanguagesLoaded(LanguageTable table) {
        onLanguagesLoaded(new LanguageLoad(languageState.begin(), settings.copy(), table));
    }

    public static final class LanguageLoad {
        private final long revision;
        private final SearchSettings settings;
        private final LanguageTable table;

        private LanguageLoad(long revision, SearchSettings settings, LanguageTable table) {
            this.revision = revision;
            this.settings = settings;
            this.table = table;
        }
    }

    public static void ensureLanguagesLoaded() {
        if (resourceReloadRevision != 0 || languageState.pending() || languageState.value().matchesRequest(settings)
                || (languageFailedAt != 0 && System.nanoTime() - languageFailedAt < 1_000_000_000L)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            minecraft.execute(() -> {
                resourcesReady = true;
                reloadLanguagesIfNeeded();
            });
        }
    }

    public static void warmUp() {
        if (isEnabled()) {
            ensureLanguagesLoaded();
        }
    }

    public static void openConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft != null && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen(null));
        }
    }

    public static void setConfigFile(Path file) {
        configFile = file;
    }

    public static void applyAndSave(SearchSettings updated) {
        SearchSettings incoming = updated.copy();
        incoming.sanitize();
        boolean changed = !incoming.equals(settings);
        setSettings(incoming);
        Path file = configFile;
        if (file != null && (changed || !java.nio.file.Files.exists(file))) {
            ConfigIo.save(file, settings);
        }
        if (changed) {
            notifySettingsApplied();
        }
    }

    public static void onSettingsApplied(Runnable listener) {
        settingsAppliedListeners.addIfAbsent(java.util.Objects.requireNonNull(listener));
    }

    public static void onInvalidate(Runnable listener) {
        invalidateListeners.addIfAbsent(java.util.Objects.requireNonNull(listener));
    }

    private static void notifySettingsApplied() {
        runListeners(settingsAppliedListeners);
    }

    private static void runListeners(List<Runnable> listeners) {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException error) {
                com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
                BetterSearch.LOGGER.debug("[{}] listener failed", BetterSearch.MOD_NAME, error);
            }
        }
    }

    public static synchronized void invalidate() {
        languageStamp.incrementAndGet();
        index.invalidate();
        clearQueryCache();
        creativeOrder = Collections.emptyMap();
        creativeOrderSource = null;
        creativeOrderSize = -1;
        RecipeSearch.invalidate();
        CommandItemIndex.invalidate();
        runListeners(invalidateListeners);
    }

    private static void clearQueryCache() {
        queryCache = null;
    }

    public static boolean isEnabled() {
        return settings.enabled;
    }

    public static void prepare(Collection<ItemStack> displayItems) {
        if (isEnabled() && settings.searchCreative && displayItems != null) {
            rememberCreativeOrder(displayItems);
            ensureIndex(displayItems);
        }
    }

    public static Map<Item, Integer> creativeOrder() {
        Minecraft minecraft = Minecraft.getInstance();
        if (creativeOrder.isEmpty() && minecraft != null && creativeOrderRequested.compareAndSet(false, true)) {
            minecraft.execute(() -> {
                try {
                    ensureCreativeOrder();
                    if (!creativeOrder.isEmpty()) {
                        notifySettingsApplied();
                    }
                } finally {
                    creativeOrderRequested.set(false);
                }
            });
        }
        return creativeOrder;
    }

    private static void ensureCreativeOrder() {
        if (!creativeOrder.isEmpty()) {
            return;
        }
        try {
            rememberCreativeOrder(net.minecraft.world.item.CreativeModeTabs.searchTab().getDisplayItems());
        } catch (RuntimeException ignored) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);

        }
    }

    private static synchronized void rememberCreativeOrder(Collection<ItemStack> pool) {
        long fingerprint = 1;
        for (ItemStack stack : pool) {
            fingerprint = fingerprint * 31 + System.identityHashCode(stack);
        }
        if (creativeOrderSource == pool && creativeOrderSize == pool.size()
                && creativeOrderFingerprint == fingerprint) {
            return;
        }
        Map<Item, Integer> order = new java.util.IdentityHashMap<>(pool.size());
        int at = 0;
        for (ItemStack stack : pool) {
            if (stack != null && !stack.isEmpty()) {
                order.putIfAbsent(stack.getItem(), at++);
            }
        }
        if (creativeOrderSource == pool && creativeOrderFingerprint != fingerprint) {
            index.invalidate();
            clearQueryCache();
        }
        creativeOrder = Collections.unmodifiableMap(order);
        creativeOrderSource = pool;
        creativeOrderSize = pool.size();
        creativeOrderFingerprint = fingerprint;
    }

    public static List<ItemStack> search(String rawQuery, Collection<ItemStack> displayItems) {
        SearchSettings snapshot = settings;
        if (!snapshot.enabled || !snapshot.searchCreative || rawQuery == null || displayItems == null) {
            return null;
        }
        rememberCreativeOrder(displayItems);
        SearchIndex<ItemStack> current = ensureIndex(displayItems);
        if (current == null) {
            return null;
        }
        SearchCache cached = queryCache;
        if (cached != null && current == cached.index && snapshot == cached.settings && rawQuery.equals(cached.query)) {
            return cached.results;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, snapshot);
            if (query.isEmpty()) {
                return null;
            }
            List<ItemStack> results = Collections.unmodifiableList(new ArrayList<>(current.search(query, snapshot)));
            queryCache = new SearchCache(rawQuery, current, snapshot, results);
            return results;
        } catch (RuntimeException error) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(error);
            index.invalidate();
            clearQueryCache();
            BetterSearch.LOGGER.error("[{}] search failed, falling back to vanilla", BetterSearch.MOD_NAME, error);
            return null;
        }
    }

    private static final class SearchCache {
        private final String query;
        private final SearchIndex<ItemStack> index;
        private final SearchSettings settings;
        private final List<ItemStack> results;

        private SearchCache(String query, SearchIndex<ItemStack> index, SearchSettings settings, List<ItemStack> results) {
            this.query = query;
            this.index = index;
            this.settings = settings;
            this.results = results;
        }
    }

    private static SearchIndex<ItemStack> ensureIndex(Collection<ItemStack> source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        return index.getPrepared(source, source.size(), languageStamp.get(), () -> {
            Player player = minecraft.player;
            if (player == null) {
                throw new IllegalStateException("Client player is unavailable");
            }
            return CreativeIndexBuilder.prepare(new ArrayList<>(source), languages(), settings.copy(),
                    Item.TooltipContext.of(minecraft.level), player);
        }, () -> {
            clearQueryCache();
            refreshOpenSearch(minecraft);
        });
    }

    private static void refreshOpenSearch(Minecraft minecraft) {
        try {
            if (minecraft.gui.screen() instanceof com.rivalzin.bettersearch.mixin.CreativeScreenAccessor open) {
                net.minecraft.client.gui.components.EditBox box = open.bettersearch$searchBox();
                if (box != null && box.isVisible() && !box.getValue().isEmpty()) {
                    open.bettersearch$refreshSearch();
                }
            }
        } catch (RuntimeException ignored) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
        }
    }
}
