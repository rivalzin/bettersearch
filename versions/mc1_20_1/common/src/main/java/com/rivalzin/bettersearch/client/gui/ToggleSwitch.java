package com.rivalzin.bettersearch.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

// the knob animates on render time, not on tick
public final class ToggleSwitch extends AbstractWidget {
    public static final int WIDTH = 28;
    public static final int HEIGHT = 14;
    private static final int KNOB_WIDTH = 10;
    private static final long ANIMATION_MS = 140L;

    private final Consumer<Boolean> onChange;
    private boolean value;
    private float animationFrom;
    private long animationStart;

    public ToggleSwitch(int x, int y, boolean value, Component narration, Consumer<Boolean> onChange) {
        super(x, y, WIDTH, HEIGHT, narration);
        this.value = value;
        this.onChange = onChange;
        this.animationFrom = value ? 1.0F : 0.0F;
        this.animationStart = 0L;
    }


    @Override
    @SuppressWarnings("deprecation")
    public void onClick(double mouseX, double mouseY) {
        set(!value);
    }

    private void set(boolean newValue) {
        if (newValue != value) {
            animationFrom = animation();
            animationStart = Util.getMillis();
            value = newValue;
            onChange.accept(newValue);
        }
    }

    private float animation() {
        float target = value ? 1.0F : 0.0F;
        long elapsed = Util.getMillis() - animationStart;
        if (elapsed < 0L || elapsed >= ANIMATION_MS) {
            return target;
        }
        float progress = elapsed / (float) ANIMATION_MS;
        progress = progress * progress * (3.0F - 2.0F * progress);
        return animationFrom + (target - animationFrom) * progress;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float position = animation();
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int track = this.active
                ? Theme.blend(Theme.SWITCH_OFF, Theme.SWITCH_ON, position)
                : Theme.SWITCH_DISABLED;
        guiGraphics.fill(x, y, right, bottom, Theme.BORDER);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, track);

        int knobX = x + 1 + Math.round((getWidth() - 2 - KNOB_WIDTH) * position);
        int knob = !this.active
                ? Theme.KNOB_DISABLED
                : (isHovered() ? Theme.KNOB_HOVER : Theme.KNOB);
        guiGraphics.fill(knobX, y + 2, knobX + KNOB_WIDTH, bottom - 2, knob);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button",
                Component.empty().append(getMessage()).append(": ").append(CommonComponents.optionStatus(value))));
    }
}
