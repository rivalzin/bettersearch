package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiButton;

import java.util.Map;
import java.util.WeakHashMap;

public final class Tips {
    private static final Map<GuiButton, String> TIPS = new WeakHashMap<>();

    private Tips() {
    }

    public static void set(GuiButton widget, String tip) {
        if (widget != null && tip != null) {
            TIPS.put(widget, tip);
        }
    }

    public static String of(GuiButton widget) {
        return widget == null ? null : TIPS.get(widget);
    }
}
