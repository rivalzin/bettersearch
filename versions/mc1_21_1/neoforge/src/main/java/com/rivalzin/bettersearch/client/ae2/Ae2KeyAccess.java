package com.rivalzin.bettersearch.client.ae2;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public interface Ae2KeyAccess {
    Component getDisplayName();
    String getModId();
    Object getPrimaryKey();
    ResourceLocation getId();
}
