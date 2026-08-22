package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.nio.file.Path;

@Mod(value = BetterSearch.MOD_ID, dist = Dist.CLIENT)
public final class BetterSearchNeoForge {
    public BetterSearchNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        modEventBus.addListener(BetterSearchKeys::onRegisterKeyMappings);
        registerReloadListener(modEventBus);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new BetterSearchConfigScreen(modListScreen));
        NeoForge.EVENT_BUS.addListener(BetterSearchKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(BetterSearchNeoForge::onClientTick);

        BetterSearch.LOGGER.info("[{}] loaded, config: {}", BetterSearch.MOD_NAME, configFile);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        com.rivalzin.bettersearch.client.BetterSearchClient.ensureLanguagesLoaded();
    }

    private static void registerReloadListener(IEventBus modEventBus) {
        String[][] halves = {
                {"net.neoforged.neoforge.client.event.AddClientReloadListenersEvent",
                 "com.rivalzin.bettersearch.neoforge.ReloadHook"},
                {"net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent",
                 "com.rivalzin.bettersearch.neoforge.LegacyReloadHook"},
        };
        for (String[] half : halves) {
            try {
                Class.forName(half[0]);
                Class.forName(half[1]).getMethod("install", IEventBus.class).invoke(null, modEventBus);
                return;
            } catch (ClassNotFoundException otherHalf) {
            } catch (Throwable t) {
                BetterSearch.LOGGER.warn("[{}] failed to register reload listener: {}",
                        BetterSearch.MOD_NAME, t.toString());
                return;
            }
        }
        BetterSearch.LOGGER.warn("[{}] no known reload event on this NeoForge, F3+T will not refresh the language table",
                BetterSearch.MOD_NAME);
    }
}
