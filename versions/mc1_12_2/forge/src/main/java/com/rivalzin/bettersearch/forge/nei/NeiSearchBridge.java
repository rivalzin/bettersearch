package com.rivalzin.bettersearch.forge.nei;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.FailurePolicy;
import com.rivalzin.bettersearch.async.AsyncIndexState;
import com.rivalzin.bettersearch.client.CreativeIndex;
import com.rivalzin.bettersearch.client.LangTable;
import com.rivalzin.bettersearch.client.ModConfig;
import com.rivalzin.bettersearch.core.SearchIndex;
import com.rivalzin.bettersearch.core.SearchQuery;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

final class NeiSearchBridge {
    private final AsyncIndexState<SearchIndex<ItemStack>> index = new AsyncIndexState<>(
            task -> Minecraft.getMinecraft().addScheduledTask(task), ForkJoinPool.commonPool(),
            task -> Minecraft.getMinecraft().addScheduledTask(task),
            error -> BetterSearch.LOGGER.debug("[{}] NEI search fallback: {}", BetterSearch.MOD_NAME, error.toString()));
    private final Field items;
    private final Runnable refresh;
    private volatile String requested = "";
    private volatile Context context;
    private Cache cache;

    NeiSearchBridge(Field items, Runnable refresh) {
        this.items = items;
        this.refresh = refresh;
    }

    void request(String query) {
        requested = query;
    }

    synchronized void tick() throws IllegalAccessException {
        Minecraft minecraft = Minecraft.getMinecraft();
        SearchSettings settings = ModConfig.settings();
        if (minecraft.player == null || !settings.enabled || !settings.searchNei) {
            if (context != null) {
                index.invalidate();
                context = null;
                cache = null;
                refresh.run();
            }
            return;
        }
        if (requested.isEmpty()) {
            return;
        }
        LangTable.ensure(settings);
        Object list = items.get(null);
        if (!(list instanceof List<?>)) {
            return;
        }
        List<?> source = (List<?>) list;
        int config = ModConfig.stamp();
        int languageStamp = LangTable.stamp();
        String language = minecraft.gameSettings.language;
        Context current = context;
        if (current == null || current.source != source || current.size != source.size()
                || current.config != config || current.languageStamp != languageStamp
                || !current.language.equals(language) || current.player != minecraft.player) {
            index.invalidate();
            SearchSettings captured = settings.copy();
            captured.maxResults = 0;
            captured.sortByRelevance = false;
            current = new Context(source, minecraft.player, language, languageStamp, config, captured);
            context = current;
            cache = null;
        }
        Context captured = current;
        index.getPrepared(captured, captured.size, 0L, () -> {
            List<ItemStack> stacks = new ArrayList<>(captured.size);
            for (Object value : captured.source) {
                if (value instanceof ItemStack && !((ItemStack) value).isEmpty()) {
                    stacks.add((ItemStack) value);
                }
            }
            return CreativeIndex.prepare(stacks, captured.settings);
        }, refresh);
    }

    synchronized boolean matches(ItemStack stack, String query) {
        SearchSettings settings = ModConfig.settings();
        Context current = context;
        if (!settings.enabled || !settings.searchNei || current == null || stack == null || stack.isEmpty()
                || current.config != ModConfig.stamp()) {
            return false;
        }
        SearchIndex<ItemStack> ready = index.ready(current, current.size, 0L);
        if (ready == null) {
            return false;
        }
        try {
            Cache found = cache;
            if (found == null || found.index != ready || !query.equals(found.query)) {
                Set<Key> keys = new HashSet<>();
                SearchQuery parsed = SearchQuery.parse(query, current.settings);
                if (!parsed.isEmpty()) {
                    for (ItemStack result : ready.search(parsed, current.settings)) {
                        keys.add(new Key(result, true));
                    }
                }
                found = new Cache(ready, query, keys);
                cache = found;
            }
            return found.keys.contains(new Key(stack, false));
        } catch (RuntimeException | LinkageError error) {
            FailurePolicy.rethrowFatal(error);
            return false;
        }
    }

    private static final class Context {
        final List<?> source;
        final int size;
        final Object player;
        final String language;
        final int languageStamp;
        final int config;
        final SearchSettings settings;

        Context(List<?> source, Object player, String language, int languageStamp, int config, SearchSettings settings) {
            this.source = source;
            this.size = source.size();
            this.player = player;
            this.language = language;
            this.languageStamp = languageStamp;
            this.config = config;
            this.settings = settings;
        }
    }

    private static final class Cache {
        final SearchIndex<ItemStack> index;
        final String query;
        final Set<Key> keys;

        Cache(SearchIndex<ItemStack> index, String query, Set<Key> keys) {
            this.index = index;
            this.query = query;
            this.keys = keys;
        }
    }

    private static final class Key {
        final Item item;
        final int damage;
        final NBTTagCompound tag;

        Key(ItemStack stack, boolean copy) {
            item = stack.getItem();
            damage = stack.getMetadata();
            NBTTagCompound current = stack.getTagCompound();
            tag = copy && current != null ? current.copy() : current;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Key)) {
                return false;
            }
            Key other = (Key) value;
            return item == other.item && damage == other.damage && Objects.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            return (System.identityHashCode(item) * 31 + damage) * 31 + Objects.hashCode(tag);
        }
    }
}
