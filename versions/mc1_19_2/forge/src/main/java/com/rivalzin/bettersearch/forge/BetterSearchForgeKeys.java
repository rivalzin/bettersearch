package com.rivalzin.bettersearch.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.KeyConflictGuard;
import com.rivalzin.bettersearch.client.ShortcutWatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchForgeKeys {
    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.bettersearch.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private static boolean pending;

    private BetterSearchForgeKeys() {
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        KeyConflictGuard.listenAlt(() -> Screen.hasAltDown());

        KeyConflictGuard.holdOnly(mapping -> mapping.getKeyModifier() == KeyModifier.NONE);

        ShortcutWatcher.listen(BetterSearchForgeKeys::onKeyPress);
    }

    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        KeyConflictGuard.update(OPEN_CONFIG, OPEN_CONFIG.getKeyModifier() == KeyModifier.ALT);

        while (OPEN_CONFIG.consumeClick()) {
            pending = true;
        }
        if (pending) {
            pending = false;
            BetterSearchClient.openConfigScreen();
        }
    }

    private static void onKeyPress(String keyName) {
        if (!OPEN_CONFIG.isUnbound() && OPEN_CONFIG.getKeyModifier() == KeyModifier.ALT
                && keyName.equals(OPEN_CONFIG.saveString()) && Screen.hasAltDown()) {
            pending = true;
        }
    }
}
