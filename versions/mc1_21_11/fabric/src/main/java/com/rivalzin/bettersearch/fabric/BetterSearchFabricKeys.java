package com.rivalzin.bettersearch.fabric;

import com.rivalzin.bettersearch.client.BetterSearchClient;
import com.rivalzin.bettersearch.client.KeyConflictGuard;
import com.rivalzin.bettersearch.client.ShortcutWatcher;
import com.rivalzin.bettersearch.core.ShortcutRule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    // written inside the press, read at the end of the tick
    private static boolean pending;

    private BetterSearchFabricKeys() {
    }

    /** The Alt belongs to the default key: moved anywhere else, the key answers on its own. */
    static boolean needsAlt() {
        return OPEN_CONFIG.isDefault();
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);
        ShortcutWatcher.listen(BetterSearchFabricKeys::onKeyPress);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyConflictGuard.update(OPEN_CONFIG, needsAlt(), client.hasAltDown());
            while (OPEN_CONFIG.consumeClick()) {
                // the press already came in through ShortcutWatcher, with the Alt read at the
                // right moment; the copy vanilla kept is dropped here
            }
            if (pending) {
                pending = false;
                BetterSearchClient.openConfigScreen();
            }
        });
    }

    // runs inside the press, so the Alt is still under the finger when it is read
    private static void onKeyPress(String keyName) {
        String bound = OPEN_CONFIG.isUnbound() ? null : OPEN_CONFIG.saveString();
        if (ShortcutRule.opens(keyName, bound, needsAlt(), Minecraft.getInstance().hasAltDown())) {
            pending = true;
        }
    }
}
