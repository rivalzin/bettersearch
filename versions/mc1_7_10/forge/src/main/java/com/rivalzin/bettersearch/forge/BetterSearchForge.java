package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.ModConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = BetterSearch.MOD_ID,
        name = BetterSearch.MOD_NAME,
        version = BetterSearch.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        guiFactory = "com.rivalzin.bettersearch.forge.ConfigGuiFactory")
public final class BetterSearchForge {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(new java.io.File(event.getModConfigurationDirectory(),
                "bettersearch.json").toPath(), loadedModCount());
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {

            MinecraftForge.EVENT_BUS.register(new SearchHook());
            FMLCommonHandler.instance().bus().register(new Keybinds());
            FMLCommonHandler.instance().bus().register(new IndexWarmer());
            FMLCommonHandler.instance().bus().register(new com.rivalzin.bettersearch.forge.ae2.Ae2ClientEvents());
            BetterSearch.LOGGER.info("[{}] loaded (1.7.10), log backend: {}",
                    BetterSearch.MOD_NAME, BetterSearch.LOGGER.backend());
        }
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
