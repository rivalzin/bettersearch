package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.nio.file.Path;

@Mod(value = BetterSearch.MOD_ID, dist = Dist.CLIENT)
public final class BetterSearchNeoForge {
    public BetterSearchNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile, loadedModCount());
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        modEventBus.addListener(BetterSearchNeoForge::onRegisterClientReloadListeners);
        modEventBus.addListener(BetterSearchKeys::onRegisterKeyMappings);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new BetterSearchConfigScreen(modListScreen));
        NeoForge.EVENT_BUS.addListener(BetterSearchKeys::onClientTick);

        BetterSearch.LOGGER.info("[{}] loaded, config: {}", BetterSearch.MOD_NAME, configFile);
    }

    private static void onRegisterClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages"),
                new LanguageReloadListener());
    }

    private static int loadedModCount() {
        return (int) ModList.get().getMods().stream()
                .filter(mod -> !"minecraft".equals(mod.getModId())
                        && !"forge".equals(mod.getModId())
                        && !"neoforge".equals(mod.getModId())
                        && !"mcp".equals(mod.getModId()))
                .count();
    }
}
