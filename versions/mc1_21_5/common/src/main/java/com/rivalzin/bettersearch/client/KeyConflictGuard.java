package com.rivalzin.bettersearch.client;

import net.minecraft.client.KeyMapping;

public final class KeyConflictGuard {
    private static volatile KeyMapping shortcut;

    private static volatile String heldBackKey;

    private static volatile java.util.function.BooleanSupplier altProbe;

    private static volatile java.util.function.Predicate<KeyMapping> holdable;

    private KeyConflictGuard() {
    }

    public static void listenAlt(java.util.function.BooleanSupplier probe) {
        altProbe = probe;
    }

    public static void holdOnly(java.util.function.Predicate<KeyMapping> filter) {
        holdable = filter;
    }

    public static void update(KeyMapping mapping, boolean shortcutUsesAlt) {
        shortcut = mapping;
        heldBackKey = shortcutUsesAlt && mapping != null && !mapping.isUnbound()
                ? mapping.saveString()
                : null;
    }

    public static boolean holdsBack(KeyMapping mapping) {
        String key = heldBackKey;
        if (key == null || mapping == shortcut) {
            return false;
        }

        if (!key.equals(mapping.saveString())) {
            return false;
        }
        java.util.function.Predicate<KeyMapping> filter = holdable;
        if (filter != null && !filter.test(mapping)) {
            return false;
        }
        java.util.function.BooleanSupplier probe = altProbe;
        return probe != null && probe.getAsBoolean();
    }
}
