package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = BetterSearch.MOD_ID,
        name = BetterSearch.MOD_NAME,
        version = BetterSearch.VERSION,
        clientSideOnly = true,
        acceptedMinecraftVersions = "[1.12.2]")
public final class BetterSearchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(new java.io.File(event.getModConfigurationDirectory(),
                "bettersearch.json").toPath(), loadedModCount());
        MinecraftForge.EVENT_BUS.register(new SearchHook());
        MinecraftForge.EVENT_BUS.register(new Keybinds());
        MinecraftForge.EVENT_BUS.register(new com.rivalzin.bettersearch.forge.ae2.Ae2ClientEvents());
        BetterSearch.LOGGER.info("[{}] loaded (1.12.2), log backend: {}",
                BetterSearch.MOD_NAME, BetterSearch.LOGGER.backend());
    }

    private static int loadedModCount() {
        return (int) Loader.instance().getActiveModList().stream()
                .filter(mod -> !"minecraft".equalsIgnoreCase(mod.getModId())
                        && !"mcp".equalsIgnoreCase(mod.getModId())
                        && !"FML".equalsIgnoreCase(mod.getModId())
                        && !"Forge".equalsIgnoreCase(mod.getModId()))
                .count();
    }
}
