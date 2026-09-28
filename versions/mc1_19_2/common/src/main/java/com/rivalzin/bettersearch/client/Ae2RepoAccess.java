package com.rivalzin.bettersearch.client;

import net.minecraft.client.gui.screens.Screen;
import java.util.Set;
public interface Ae2RepoAccess {
    Set<?> getAllEntries();
    String getSearchString();
    void updateView();
    void setUpdateViewListener(Runnable listener);
    void bettersearch$owner(Screen screen);
    void bettersearch$tick();
    void bettersearch$close();
}
