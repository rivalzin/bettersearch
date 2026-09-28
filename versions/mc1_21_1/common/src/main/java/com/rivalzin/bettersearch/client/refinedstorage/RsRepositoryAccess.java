package com.rivalzin.bettersearch.client.refinedstorage;

import net.minecraft.client.gui.screens.Screen;

public interface RsRepositoryAccess {
    void bettersearch$rsOpen(Screen owner);
    void bettersearch$rsQuery(String query);
    void bettersearch$rsTick();
    void bettersearch$rsClose();
    boolean bettersearch$rsMatches(Object resource, Object literal);
}
