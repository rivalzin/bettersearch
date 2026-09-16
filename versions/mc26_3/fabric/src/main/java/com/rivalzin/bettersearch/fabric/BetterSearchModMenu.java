package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class BetterSearchModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BetterSearchConfigScreen::new;
    }
}
