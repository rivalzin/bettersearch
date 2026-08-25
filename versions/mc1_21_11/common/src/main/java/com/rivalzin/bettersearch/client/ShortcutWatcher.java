package com.rivalzin.bettersearch.client;

/**
 * Vanilla gives one physical key to a single control, so a mod sitting on the same key leaves
 * consumeClick deaf and the shortcut never fires. KeyMapping.click still runs on every press,
 * whoever owns the key, and that is where Fabric reads the shortcut. Forge and NeoForge route
 * the press by modifier themselves and never arm this.
 */
public final class ShortcutWatcher {
    /** Called on the client thread, inside the press, with the name of the key that went down. */
    public interface Press {
        void pressed(String keyName);
    }

    private static volatile Press listener;

    private ShortcutWatcher() {
    }

    public static void listen(Press press) {
        listener = press;
    }

    public static void clicked(String keyName) {
        Press press = listener;
        if (press != null) {
            press.pressed(keyName);
        }
    }
}
