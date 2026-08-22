package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.nio.file.Path;

@Mod(value = BetterSearch.MOD_ID, dist = Dist.CLIENT)
public final class BetterSearchNeoForge {
    public BetterSearchNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        modEventBus.addListener(BetterSearchNeoForge::onRegisterClientReloadListeners);
        modEventBus.addListener(BetterSearchKeys::onRegisterKeyMappings);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new BetterSearchConfigScreen(modListScreen));
        NeoForge.EVENT_BUS.addListener(BetterSearchKeys::onClientTick);

        BetterSearch.LOGGER.info("[{}] loaded, config: {}", BetterSearch.MOD_NAME, configFile);
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }
}
