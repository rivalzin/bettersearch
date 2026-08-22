package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

public final class ButtonCompat {
    private ButtonCompat() {
    }

    // the button constructor changed shape almost every version
    public static Builder builder(String text, Consumer<GuiButton> action) {
        return new Builder(text, action);
    }

    public static final class ActionButton extends GuiButton implements Pressable {
        private final Consumer<GuiButton> action;

        ActionButton(int x, int y, int width, int height, String text, Consumer<GuiButton> action) {
            super(0, x, y, width, height, text);
            this.action = action;
        }

        @Override
        public void onPress() {
            action.accept(this);
        }
    }

    public static final class Builder {
        private final String text;
        private final Consumer<GuiButton> action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private String tip;

        private Builder(String text, Consumer<GuiButton> action) {
            this.text = text;
            this.action = action;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder tooltip(String tip) {
            this.tip = tip;
            return this;
        }

        public GuiButton build() {
            ActionButton button = new ActionButton(x, y, width, height, text, action);
            Tips.set(button, tip);
            return button;
        }
    }
}
