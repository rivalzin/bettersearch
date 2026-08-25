package com.rivalzin.bettersearch.client;

import net.minecraft.client.KeyMapping;

/**
 * Xaero's Minimap opens on plain O, and so do plenty of other mods. With our shortcut on
 * Alt + O both answer the same press. While Alt is held, every other control bound to the
 * same key stands down. Move the shortcut and the hold moves with it; take the Alt off it
 * and nothing is held back any more.
 */
public final class KeyConflictGuard {
    private static volatile KeyMapping shortcut;

    // the key the shortcut sits on, or null when there is nothing to hold back. saveString()
    // is the one way to read it that every version and both loaders agree on, and it hands
    // back the same cached string every time
    private static volatile String heldBackKey;

    private static volatile boolean altDown;

    private KeyConflictGuard() {
    }

    /** Read once per client tick, so a keybind poll only costs a couple of field reads. */
    public static void update(KeyMapping mapping, boolean shortcutUsesAlt, boolean altIsDown) {
        shortcut = mapping;
        altDown = altIsDown;
        heldBackKey = shortcutUsesAlt && mapping != null && !mapping.isUnbound()
                ? mapping.saveString()
                : null;
    }

    public static boolean holdsBack(KeyMapping mapping) {
        String key = heldBackKey;
        if (key == null || !altDown || mapping == shortcut) {
            return false;
        }
        return key.equals(mapping.saveString());
    }
}
