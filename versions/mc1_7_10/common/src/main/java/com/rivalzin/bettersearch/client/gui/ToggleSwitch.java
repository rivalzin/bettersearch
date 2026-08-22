package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import java.util.function.Consumer;

// the knob animates on render time, not on tick
public final class ToggleSwitch extends GuiButton implements Pressable {
    public static final int WIDTH = 28;
    public static final int HEIGHT = 14;
    private static final int KNOB_WIDTH = 10;
    private static final long ANIMATION_MS = 140L;

    private final Consumer<Boolean> onChange;
    private boolean value;
    private float animationFrom;
    private long animationStart;

    public ToggleSwitch(int x, int y, boolean value, String narration, Consumer<Boolean> onChange) {
        super(0, x, y, WIDTH, HEIGHT, narration);
        this.value = value;
        this.onChange = onChange;
        this.animationFrom = value ? 1.0F : 0.0F;
        this.animationStart = 0L;
    }

    public boolean value() {
        return value;
    }

    @Override
    public void onPress() {
        set(!value);
    }

    private void set(boolean newValue) {
        if (newValue != value) {
            animationFrom = animation();
            animationStart = Minecraft.getSystemTime();
            value = newValue;
            onChange.accept(newValue);
        }
    }

    private float animation() {
        float target = value ? 1.0F : 0.0F;
        long elapsed = Minecraft.getSystemTime() - animationStart;
        if (elapsed < 0L || elapsed >= ANIMATION_MS) {
            return target;
        }
        float progress = elapsed / (float) ANIMATION_MS;
        progress = progress * progress * (3.0F - 2.0F * progress);
        return animationFrom + (target - animationFrom) * progress;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        boolean about = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        float position = animation();
        int x = this.xPosition;
        int y = this.yPosition;
        int right = x + this.width;
        int bottom = y + this.height;

        int track = this.enabled
                ? Theme.blend(Theme.SWITCH_OFF, Theme.SWITCH_ON, position)
                : Theme.SWITCH_DISABLED;
        Gui.drawRect(x, y, right, bottom, Theme.BORDER);
        Gui.drawRect(x + 1, y + 1, right - 1, bottom - 1, track);

        int knobX = x + 1 + Math.round((this.width - 2 - KNOB_WIDTH) * position);
        int knob = !this.enabled
                ? Theme.KNOB_DISABLED
                : (about ? Theme.KNOB_HOVER : Theme.KNOB);
        Gui.drawRect(knobX, y + 2, knobX + KNOB_WIDTH, bottom - 2, knob);
    }
}
