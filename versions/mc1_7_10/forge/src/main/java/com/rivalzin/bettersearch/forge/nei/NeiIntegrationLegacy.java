package com.rivalzin.bettersearch.forge.nei;

import com.rivalzin.bettersearch.BetterSearch;
import codechicken.nei.api.API;

public final class NeiIntegrationLegacy {
    private NeiIntegrationLegacy() {
    }

    public static void install() {
        API.addSearchProvider(new NeiSearchProviderLegacy());
        BetterSearch.LOGGER.info("[{}] hooked into legacy NEI as a secondary provider",
                BetterSearch.MOD_NAME);
    }
}
