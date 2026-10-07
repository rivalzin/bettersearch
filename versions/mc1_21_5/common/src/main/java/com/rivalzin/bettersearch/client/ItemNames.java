package com.rivalzin.bettersearch.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;

final class ItemNames {
    private ItemNames() {
    }

    static String translationKey(ItemStack stack) {
        Component name = stack.getItemName();
        if (name.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }

        return null;
    }
}
