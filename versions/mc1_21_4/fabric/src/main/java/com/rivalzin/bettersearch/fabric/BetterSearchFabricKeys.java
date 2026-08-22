package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.gui.BetterSearchConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchFabricKeys {
    public static final String CATEGORY = "key.categories.bettersearch";

    public static final KeyMapping OPEN_CONFIG = new AltKeyMapping(
            "key.bettersearch.open_config",
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private static Screen screenAtLastTickEnd;
    private static boolean openNextTick;
    private static boolean oWasDown;

    private BetterSearchFabricKeys() {
    }

    // alt is a modifier in iris and in the shader screens, do not steal it there
    public static boolean shouldBlock(Screen screen) {
        if (screen == null || !Screen.hasAltDown() || !OPEN_CONFIG.isDefault()) {
            return false;
        }
        String name = screen.getClass().getName();
        return (name.startsWith("net.irisshaders.") || name.startsWith("net.coderbot.iris."))
                && name.contains("ShaderPackScreen");
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openNextTick) {
                openNextTick = false;
                if (!(client.screen instanceof BetterSearchConfigScreen)) {
                    client.setScreen(new BetterSearchConfigScreen(null));
                }
            }

            boolean oNow = InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_O);
            if (oNow && !oWasDown && OPEN_CONFIG.isDefault()
                    && Screen.hasAltDown() && screenAtLastTickEnd == null) {
                if (!(client.screen instanceof BetterSearchConfigScreen)) {
                    client.setScreen(new BetterSearchConfigScreen(null));
                }
                openNextTick = true;
            }
            oWasDown = oNow;

            while (OPEN_CONFIG.consumeClick()) {
                if (Screen.hasAltDown() && screenAtLastTickEnd == null) {
                    openNextTick = true;
                }
            }
            screenAtLastTickEnd = client.screen;
        });
    }
}
