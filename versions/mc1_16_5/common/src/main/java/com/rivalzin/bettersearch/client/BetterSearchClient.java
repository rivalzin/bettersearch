package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BetterSearchClient {
    private static SearchSettings settings = new SearchSettings();
    private static volatile LanguageTable languages = LanguageTable.EMPTY;
    private static Path configFile;

    private static volatile SearchIndex<ItemStack> index;
    private static Object indexedSource;
    private static int indexedSize = -1;
    private static long indexedStamp = -1;

    // bumped on every reload so every cache downstream knows to drop
    private static long languageStamp;
    private static boolean building;
    private static boolean resourcesReady;

    private static volatile boolean firstLoadRequested;
    // one throw and the mod stands down for the session instead of spamming
    private static boolean disabledByError;

    private static Object pendingSource;
    private static int pendingSize;

    private static String cachedQuery;
    private static List<ItemStack> cachedResults;

    private BetterSearchClient() {
    }

    public static SearchSettings settings() {
        return settings;
    }

    public static LanguageTable languages() {
        ensureFirstLoad();
        return languages;
    }

    private static void ensureFirstLoad() {
        if (resourcesReady || firstLoadRequested || !settings.crossLanguage) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        firstLoadRequested = true;
        final ResourceManager resourceManager = minecraft.getResourceManager();
        final SearchSettings snapshot = settings.copy();
        BetterSearch.LOGGER.info("[{}] resource reload never hit our listener, loading"
                + " languages directly", BetterSearch.MOD_NAME);
        CompletableFuture
                .supplyAsync(() -> LanguageTable.load(resourceManager, snapshot), Util.backgroundExecutor())
                .whenComplete((table, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] first language load failed",
                                BetterSearch.MOD_NAME, error);
                        firstLoadRequested = false;
                        return;
                    }

                    if (table == null || table.isEmpty()) {
                        BetterSearch.LOGGER.warn("[{}] a leitura por conta propria veio vazia -"
                                + " will retry on next search", BetterSearch.MOD_NAME);
                        firstLoadRequested = false;
                        return;
                    }
                    onLanguagesLoaded(table);
                }));
    }

    public static long languageStamp() {
        return languageStamp;
    }

    public static void setSettings(SearchSettings newSettings) {
        newSettings.sanitize();
        SearchSettings previous = settings;
        settings = newSettings;

        cachedQuery = null;
        cachedResults = null;

        if (previous == null || newSettings.affectsIndex(previous)) {
            invalidate();
        }
        reloadLanguagesIfNeeded();
    }

    private static void reloadLanguagesIfNeeded() {
        if (!resourcesReady || languages.matchesRequest(settings)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        final ResourceManager resourceManager = minecraft.getResourceManager();
        final SearchSettings snapshot = settings.copy();
        CompletableFuture
                .supplyAsync(() -> LanguageTable.load(resourceManager, snapshot), Util.backgroundExecutor())
                .whenComplete((table, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] failed to reload languages",
                                BetterSearch.MOD_NAME, error);
                        return;
                    }
                    onLanguagesLoaded(table);
                }));
    }

    public static void openConfigScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen == null) {
            minecraft.setScreen(new com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen(null));
        }
    }

    public static void setConfigFile(Path file) {
        configFile = file;
    }

    public static void applyAndSave(SearchSettings newSettings) {
        setSettings(newSettings.copy());
        if (configFile != null) {
            ConfigIo.save(configFile, settings);
        }
    }

    public static void onLanguagesLoaded(LanguageTable table) {
        languages = table;
        resourcesReady = true;
        languageStamp++;
        invalidate();
    }

    public static void invalidate() {
        index = null;
        indexedSource = null;
        indexedSize = -1;
        indexedStamp = -1;

        pendingSource = null;
        pendingSize = -1;
        cachedQuery = null;
        cachedResults = null;
        RecipeSearch.invalidate();
        CommandItemIndex.invalidate();

        languageStamp++;
    }

    public static boolean isEnabled() {
        return settings.enabled && !disabledByError;
    }

    public static void prepare(Collection<ItemStack> displayItems) {
        if (isEnabled() && settings.searchCreative && displayItems != null) {
            ensureIndex(displayItems);
        }
    }

    public static List<ItemStack> search(String rawQuery, Collection<ItemStack> displayItems) {
        if (!isEnabled() || !settings.searchCreative || rawQuery == null || displayItems == null) {
            return null;
        }
        SearchIndex<ItemStack> current = ensureIndex(displayItems);
        if (current == null) {
            return null;
        }
        if (rawQuery.equals(cachedQuery) && cachedResults != null) {
            return cachedResults;
        }
        try {
            SearchQuery query = SearchQuery.parse(rawQuery, settings);
            if (query.isEmpty()) {
                return null;
            }
            List<ItemStack> results = current.search(query, settings);
            cachedQuery = rawQuery;
            cachedResults = results;
            return results;
        } catch (Throwable t) {
            disabledByError = true;
            BetterSearch.LOGGER.error("[{}] search failed, falling back to vanilla",
                    BetterSearch.MOD_NAME, t);
            return null;
        }
    }

    private static SearchIndex<ItemStack> ensureIndex(Collection<ItemStack> source) {
        SearchIndex<ItemStack> current = index;
        boolean fresh = current != null
                && indexedSource == source
                && indexedSize == source.size()
                && indexedStamp == languageStamp;
        if (fresh) {
            return current;
        }
        if (!building && (pendingSource != source || pendingSize != source.size())) {
            startBuild(source);
        }
        return null;
    }

    private static void startBuild(Collection<ItemStack> source) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        final List<ItemStack> snapshot = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(source));

        final LanguageTable table = languages();
        final SearchSettings snapshotSettings = settings.copy();
        final long stamp = languageStamp;

        building = true;
        pendingSource = source;
        pendingSize = source.size();

        CompletableFuture
                .supplyAsync(() -> CreativeIndexBuilder.build(
                        snapshot, table, snapshotSettings, player), Util.backgroundExecutor())
                .whenComplete((built, error) -> minecraft.execute(() -> {
                    building = false;
                    if (error != null) {
                        BetterSearch.LOGGER.error("[{}] index build failed, using vanilla search",
                                BetterSearch.MOD_NAME, error);
                        disabledByError = true;
                        return;
                    }
                    index = built;
                    indexedSource = pendingSource;
                    indexedSize = pendingSize;
                    indexedStamp = stamp;
                    cachedQuery = null;
                    cachedResults = null;
                }));
    }
}
