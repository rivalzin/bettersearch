package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public final class FabricLanguageReloadListener extends LanguageReloadListener
        implements IdentifiableResourceReloadListener {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages");

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
