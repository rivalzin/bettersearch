package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.WeakHashMap;

public final class Tips {
    private static final Map<AbstractWidget, Component> TIPS = new WeakHashMap<>();

    private Tips() {
    }

    public static void set(AbstractWidget widget, Component tip) {
        if (widget != null && tip != null) {
            TIPS.put(widget, tip);
        }
    }

    public static Component of(AbstractWidget widget) {
        return widget == null ? null : TIPS.get(widget);
    }
}
