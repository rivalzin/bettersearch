package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.KeyConflictGuard;
import com.rivalzin.bettersearch.client.ShortcutWatcher;
import com.rivalzin.bettersearch.core.ShortcutRule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class BetterSearchFabricKeys {

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath("bettersearch", "main"));

    public static final KeyMapping OPEN_CONFIG = new AltKeyMapping(
            "key.bettersearch.open_config",
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private static boolean pending;

    private BetterSearchFabricKeys() {
    }

    static boolean needsAlt() {
        return OPEN_CONFIG.isDefault();
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ShortcutWatcher.listen(BetterSearchFabricKeys::onKeyPress);
        KeyConflictGuard.listenAlt(() -> Minecraft.getInstance().hasAltDown());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyConflictGuard.update(OPEN_CONFIG, needsAlt());
            while (OPEN_CONFIG.consumeClick()) {

            }
            if (pending) {
                pending = false;
                BetterSearchClient.openConfigScreen();
            }
        });
    }

    private static void onKeyPress(String keyName) {
        String bound = OPEN_CONFIG.isUnbound() ? null : OPEN_CONFIG.saveString();
        if (ShortcutRule.opens(keyName, bound, needsAlt(), Minecraft.getInstance().hasAltDown())) {
            pending = true;
        }
    }
}
