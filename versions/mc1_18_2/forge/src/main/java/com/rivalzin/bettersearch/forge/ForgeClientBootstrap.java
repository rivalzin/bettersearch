package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.ConfigIo;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import com.rivalzin.bettersearch.core.SearchSettings;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

final class ForgeClientBootstrap {
    private ForgeClientBootstrap() {
    }

    static void init() {
        Path configFile = FMLPaths.CONFIGDIR.get().resolve(BetterSearch.MOD_ID + ".json");
        SearchSettings settings = ConfigIo.loadOrCreate(configFile);
        BetterSearchClient.setConfigFile(configFile);
        BetterSearchClient.setSettings(settings);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ForgeClientBootstrap::onRegisterClientReloadListeners);

        BetterSearchForgeKeys.register();

        ModLoadingContext.get().registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(
                        (minecraft, parent) -> new BetterSearchConfigScreen(parent)));
        MinecraftForge.EVENT_BUS.addListener(BetterSearchForgeKeys::onClientTick);

        BetterSearch.LOGGER.info("[{}] loaded, config: {}", BetterSearch.MOD_NAME, configFile);
    }

    private static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }
}
