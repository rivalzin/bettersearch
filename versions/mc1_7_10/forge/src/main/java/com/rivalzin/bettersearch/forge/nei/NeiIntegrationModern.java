package com.rivalzin.bettersearch.forge.nei;

import com.rivalzin.bettersearch.BetterSearch;
import codechicken.nei.api.API;

public final class NeiIntegrationModern {
    private NeiIntegrationModern() {
    }

    public static void install() {

        codechicken.nei.api.GuiInfo.customSlotGuis
                .add(com.rivalzin.bettersearch.client.SearchableCreativeScreen.class);
        API.addSearchProvider(new NeiSearchProvider());
        BetterSearch.LOGGER.info("[{}] hooked into NEI (GTNH fork) as an ALWAYS provider",
                BetterSearch.MOD_NAME);
    }
}
