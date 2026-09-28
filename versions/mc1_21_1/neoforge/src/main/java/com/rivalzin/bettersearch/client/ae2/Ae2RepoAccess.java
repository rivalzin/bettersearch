package com.rivalzin.bettersearch.client.ae2;

import net.minecraft.client.gui.screens.Screen;

public interface Ae2RepoAccess {
    void setUpdateViewListener(Runnable listener);
    void bettersearch$open(Screen owner);
    void bettersearch$tick();
    void bettersearch$close();
}
