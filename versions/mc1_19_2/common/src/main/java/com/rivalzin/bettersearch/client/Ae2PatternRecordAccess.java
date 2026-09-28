package com.rivalzin.bettersearch.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
public interface Ae2PatternRecordAccess {
    long getServerId();
    String getSearchName();
    Component bettersearch$name();
    Iterable<ItemStack> bettersearch$inventory();
}
