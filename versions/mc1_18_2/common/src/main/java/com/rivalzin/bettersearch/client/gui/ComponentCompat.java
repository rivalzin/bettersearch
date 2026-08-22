package com.rivalzin.bettersearch.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

public final class ComponentCompat {
    private ComponentCompat() {
    }

    public static MutableComponent translatable(String key) {
        return new TranslatableComponent(key);
    }

    public static MutableComponent translatable(String key, Object... args) {
        return new TranslatableComponent(key, args);
    }

    public static MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    public static MutableComponent empty() {
        return new TextComponent("");
    }
}
