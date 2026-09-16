package com.rivalzin.bettersearch.forge.nei;

import codechicken.nei.api.ItemFilter;
import com.rivalzin.bettersearch.client.CreativeSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LiveFilter implements ItemFilter {
    private final String text;

    private volatile Snapshot snapshot;

    private static final class Snapshot {
        final Object indexMemo;
        final int configMemo;
        final Set<String> keys;

        final boolean anyNbt;

        Snapshot(Object indexMemo, int configMemo, Set<String> keys, boolean anyNbt) {
            this.indexMemo = indexMemo;
            this.configMemo = configMemo;
            this.keys = keys;
            this.anyNbt = anyNbt;
        }
    }

    LiveFilter(String text) {
        this.text = text;
    }

    @Override
    public boolean matches(ItemStack stack) {
        Snapshot current = currentSnapshot();
        if (current == null || current.keys == null) {
            return false;
        }
        try {
            if (!current.anyNbt && stack.hasTagCompound()) {

                return false;
            }
            return current.keys.contains(CreativeSearch.stackKey(stack));
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            return false;
        }
    }

    private Snapshot currentSnapshot() {
        Object index = CreativeSearch.currentIndex();
        int settings = ModConfig.stamp();
        Snapshot current = snapshot;
        if (current != null && current.indexMemo == index && current.configMemo == settings) {
            return current;
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.indexMemo == index && current.configMemo == settings) {
                return current;
            }
            List<ItemStack> result = CreativeSearch.searchForViewer(text);
            Set<String> keys = null;
            boolean anyNbt = false;
            if (result != null) {
                keys = new HashSet<String>(result.size() * 2);
                for (ItemStack stack : result) {
                    try {
                        keys.add(CreativeSearch.stackKey(stack));
                        anyNbt |= stack.hasTagCompound();
                    } catch (Exception | LinkageError ignored) {
                        com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(ignored);
                    }
                }
            }
            current = new Snapshot(index, settings, keys, anyNbt);
            snapshot = current;
            return current;
        }
    }
}
