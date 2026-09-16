package com.rivalzin.bettersearch.client;

import net.minecraft.client.input.KeyEvent;

import java.util.function.Predicate;

public final class ShortcutWatcher {

    public interface Press {
        void pressed(String keyName);
    }

    private static volatile Press listener;

    private static volatile Predicate<KeyEvent> priorityShortcut;

    private ShortcutWatcher() {
    }

    public static void listen(Press press) {
        listener = press;
    }

    public static void prioritize(Predicate<KeyEvent> shortcut) {
        priorityShortcut = shortcut;
    }

    public static boolean claims(KeyEvent event) {
        Predicate<KeyEvent> shortcut = priorityShortcut;
        return event.hasAltDown() && shortcut != null && shortcut.test(event);
    }

    public static void clicked(String keyName) {
        Press press = listener;
        if (press != null) {
            press.pressed(keyName);
        }
    }
}
