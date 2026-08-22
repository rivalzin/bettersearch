package com.rivalzin.bettersearch.client;

import com.rivalzin.bettersearch.BetterSearch;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

// F3+T lands here, that is the only time the table is rebuilt
public class LanguageReloadListener extends SimplePreparableReloadListener<LanguageTable> {
    @Override
    protected LanguageTable prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try {
            return LanguageTable.load(resourceManager, BetterSearchClient.settings());
        } catch (Throwable t) {
            BetterSearch.LOGGER.error("[{}] failed to read languages, cross-language search stays"
                    + " off for this session",
                    BetterSearch.MOD_NAME, t);
            return LanguageTable.EMPTY;
        }
    }

    @Override
    protected void apply(LanguageTable table, ResourceManager resourceManager, ProfilerFiller profiler) {
        BetterSearchClient.onLanguagesLoaded(table);
    }
}
