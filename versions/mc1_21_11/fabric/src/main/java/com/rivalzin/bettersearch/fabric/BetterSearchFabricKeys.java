package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchFabricKeys {
    @SuppressWarnings("deprecation")
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("bettersearch", "main"));

    public static final KeyMapping OPEN_CONFIG = new AltKeyMapping(
            "key.bettersearch.open_config",
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private BetterSearchFabricKeys() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                if (client.hasAltDown()) {
                    BetterSearchClient.openConfigScreen();
                }
            }
        });
    }
}
