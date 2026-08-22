package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ButtonCompat {
    private ButtonCompat() {
    }

    // the button constructor changed shape almost every version
    public static Builder builder(Component text, Button.OnPress action) {
        return new Builder(text, action);
    }

    public static final class Builder {
        private final Component text;
        private final Button.OnPress action;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Component tip;

        private Builder(Component text, Button.OnPress action) {
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

        public Builder tooltip(Component tip) {
            this.tip = tip;
            return this;
        }

        public Button build() {
            Button button = new Button(x, y, width, height, text, action);
            Tips.set(button, tip);
            return button;
        }
    }
}
