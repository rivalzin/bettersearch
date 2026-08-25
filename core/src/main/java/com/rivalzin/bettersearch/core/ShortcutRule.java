package com.rivalzin.bettersearch.core;

/**
 * When a key press opens the menu. Vanilla hands one physical key to a single control, so on
 * Fabric the press is read before the game matches it to a control and this is what decides
 * whether it was ours. The Alt belongs to the default key: moved anywhere else, the key
 * answers on its own.
 */
public final class ShortcutRule {
    private ShortcutRule() {
    }

    /**
     * @param pressedKey the key that went down, or null
     * @param boundKey   the key the shortcut sits on, or null when it is unbound
     * @param atDefault  the shortcut is still on the key it shipped with
     * @param altDown    Alt was held at the moment of the press
     */
    public static boolean opens(String pressedKey, String boundKey, boolean atDefault, boolean altDown) {
        if (pressedKey == null || boundKey == null || !pressedKey.equals(boundKey)) {
            return false;
        }
        return altDown || !atDefault;
    }
}
