package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public final class FabricLanguageReloadListener extends LanguageReloadListener
        implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
