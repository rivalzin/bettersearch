package com.rivalzin.bettersearch.forge.nei;

import com.rivalzin.bettersearch.BetterSearch;
import codechicken.nei.api.API;

public final class NeiIntegrationModern {
    private NeiIntegrationModern() {
    }

    public static void install() {
        API.addSearchProvider(new NeiSearchProvider());
        BetterSearch.LOGGER.info("[{}] hooked into NEI (GTNH fork) as an ALWAYS provider",
                BetterSearch.MOD_NAME);
    }
}
