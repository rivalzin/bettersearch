package com.rivalzin.bettersearch.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class LanguageReloadListener extends SimplePreparableReloadListener<BetterSearchClient.LanguageLoad> {
    @Override
    protected BetterSearchClient.LanguageLoad prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return BetterSearchClient.loadLanguages(resourceManager);
    }

    @Override
    protected void apply(BetterSearchClient.LanguageLoad load, ResourceManager resourceManager, ProfilerFiller profiler) {
        BetterSearchClient.onLanguagesLoaded(load);
    }
}
