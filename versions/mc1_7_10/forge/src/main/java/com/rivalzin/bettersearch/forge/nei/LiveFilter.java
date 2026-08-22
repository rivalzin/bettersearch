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
    private Object indexMemo;
    private Object configMemo;

    private Set<String> keys;

    LiveFilter(String text) {
        this.text = text;
    }

    @Override
    public boolean matches(ItemStack stack) {
        Set<String> current = currentKeys();
        if (current == null) {
            return false;
        }
        try {
            return current.contains(CreativeSearch.stackKey(stack));
        } catch (Throwable t) {
            return false;
        }
    }

    private synchronized Set<String> currentKeys() {
        Object index = CreativeSearch.currentIndex();
        Object settings = ModConfig.settings();
        if (index != indexMemo || settings != configMemo) {
            indexMemo = index;
            configMemo = settings;
            List<ItemStack> result = CreativeSearch.searchForViewer(text);
            if (result == null) {
                keys = null;
            } else {
                Set<String> updated = new HashSet<String>(result.size() * 2);
                for (ItemStack stack : result) {
                    try {
                        updated.add(CreativeSearch.stackKey(stack));
                    } catch (Throwable ignored) {
                    }
                }
                keys = updated;
            }
        }
        return keys;
    }
}
