package com.rivalzin.bettersearch.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(BetterSearch.MOD_ID, "main"));

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.bettersearch.open_config",
            KeyConflictContext.IN_GAME,
            KeyModifier.ALT,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private BetterSearchKeys() {
    }

    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN_CONFIG);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_CONFIG.consumeClick()) {
            BetterSearchClient.openConfigScreen();
        }
    }
}
