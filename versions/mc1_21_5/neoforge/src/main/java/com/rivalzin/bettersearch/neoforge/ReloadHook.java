package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

public final class ReloadHook {
    private ReloadHook() {
    }

    public static void install(IEventBus modEventBus) {
        modEventBus.addListener(ReloadHook::onRegister);
        BetterSearch.LOGGER.info("[{}] reload listener registered (renamed event, 21.4.157+)",
                BetterSearch.MOD_NAME);
    }

    private static void onRegister(AddClientReloadListenersEvent event) {
        event.addListener(ResourceLocation.fromNamespaceAndPath(BetterSearch.MOD_ID, "languages"),
                new LanguageReloadListener());
    }
}
