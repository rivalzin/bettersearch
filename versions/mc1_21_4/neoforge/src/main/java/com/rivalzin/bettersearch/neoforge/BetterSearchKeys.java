package com.rivalzin.bettersearch.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.KeyConflictGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchKeys {
    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.bettersearch.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private static boolean pending;

    private BetterSearchKeys() {
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        KeyConflictGuard.listenAlt(() -> Screen.hasAltDown());

        KeyConflictGuard.holdOnly(mapping -> mapping.getKeyModifier() == KeyModifier.NONE);
    }

    static void onClientTick(ClientTickEvent.Post event) {

        KeyConflictGuard.update(OPEN_CONFIG, OPEN_CONFIG.getKeyModifier() == KeyModifier.ALT);

        while (OPEN_CONFIG.consumeClick()) {
            pending = true;
        }
        if (pending) {
            pending = false;
            BetterSearchClient.openConfigScreen();
        }
    }
}
