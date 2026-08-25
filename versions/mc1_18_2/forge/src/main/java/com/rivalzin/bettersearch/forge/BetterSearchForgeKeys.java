package com.rivalzin.bettersearch.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.KeyConflictGuard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ClientRegistry;
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

    private BetterSearchForgeKeys() {
    }

    static void register() {
        ClientRegistry.registerKeyBinding(OPEN_CONFIG);
    }

    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // a control on the same key stands down while Alt is held; drop the Alt
        // from the shortcut and nothing is held back
        KeyConflictGuard.update(OPEN_CONFIG, OPEN_CONFIG.getKeyModifier() == KeyModifier.ALT,
                Screen.hasAltDown());

        while (OPEN_CONFIG.consumeClick()) {
            BetterSearchClient.openConfigScreen();
        }
    }
}
