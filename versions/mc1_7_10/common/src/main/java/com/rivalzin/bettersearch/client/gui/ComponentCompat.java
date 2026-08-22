package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.resources.I18n;

public final class ComponentCompat {
    private ComponentCompat() {
    }

    public static String translatable(String key) {
        return I18n.format(key);
    }

    public static String translatable(String key, Object... args) {
        return I18n.format(key, args);
    }

    public static String literal(String text) {
        return text;
    }

    public static String empty() {
        return "";
    }
}
