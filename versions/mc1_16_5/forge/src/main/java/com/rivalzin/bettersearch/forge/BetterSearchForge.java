package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(BetterSearch.MOD_ID)
public final class BetterSearchForge {
    public BetterSearchForge() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ForgeClientBootstrap::init);
    }
}
