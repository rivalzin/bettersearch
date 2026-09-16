package com.rivalzin.bettersearch.core;

public final class ShortcutRule {
    private ShortcutRule() {
    }

    public static boolean opens(String pressedKey, String boundKey, boolean atDefault, boolean altDown) {
        if (pressedKey == null || boundKey == null || !pressedKey.equals(boundKey)) {
            return false;
        }
        return altDown || !atDefault;
    }
}
