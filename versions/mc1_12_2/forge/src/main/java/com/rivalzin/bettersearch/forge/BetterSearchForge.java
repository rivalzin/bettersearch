package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = BetterSearch.MOD_ID,
        name = BetterSearch.MOD_NAME,
        version = "1.3.0",
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]")
public final class BetterSearchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(new java.io.File(event.getModConfigurationDirectory(),
                "bettersearch.json").toPath());
        MinecraftForge.EVENT_BUS.register(new SearchHook());
        MinecraftForge.EVENT_BUS.register(new Keybinds());
        BetterSearch.LOGGER.info("[{}] loaded (1.12.2), log backend: {}",
                BetterSearch.MOD_NAME, BetterSearch.LOGGER.backend());
    }
}
