package com.rivalzin.bettersearch.neoforge;

import com.rivalzin.bettersearch.client.LanguageReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class LegacyReloadHook {
    private LegacyReloadHook() {
    }

    public static void install(IEventBus modEventBus) {
        modEventBus.addListener(LegacyReloadHook::onRegister);
        com.rivalzin.bettersearch.BetterSearch.LOGGER.info(
                "[{}] reload listener registered (old event, 2024/2025 builds)",
                com.rivalzin.bettersearch.BetterSearch.MOD_NAME);
    }

    private static void onRegister(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new LanguageReloadListener());
    }
}
