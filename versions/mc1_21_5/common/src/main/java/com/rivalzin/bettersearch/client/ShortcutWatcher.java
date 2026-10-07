package com.rivalzin.bettersearch.client;

public final class ShortcutWatcher {

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
