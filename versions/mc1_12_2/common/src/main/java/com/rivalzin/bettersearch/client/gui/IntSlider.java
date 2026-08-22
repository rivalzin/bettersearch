package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

// value is stepped, the vanilla slider is 0..1 doubles
public final class IntSlider extends GuiButton {
    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<String> valueLabel;
    private final IntConsumer onChange;

    private double value;
    private boolean dragging;

    private int lastApplied;

    public IntSlider(int x, int y, int width, int height,
                     int min, int max, int step, int initialValue,
                     IntFunction<String> valueLabel, IntConsumer onChange) {
        super(0, x, y, width, height, "");
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.valueLabel = valueLabel;
        this.onChange = onChange;
        this.value = toFraction(initialValue, min, max);
        this.lastApplied = intValue();
        updateMessage();
    }

    public int intValue() {
        int raw = min + (int) Math.round(this.value * (max - min));
        int snapped = min + Math.round((raw - min) / (float) step) * step;
        return MathHelper.clamp(snapped, min, max);
    }

    private void updateMessage() {
        this.displayString = valueLabel.apply(intValue());
    }

    private void applyValue() {
        int now = intValue();
        if (now != lastApplied) {
            lastApplied = now;
            onChange.accept(now);
        }
    }

    private void setFromMouse(int mouseX) {
        this.value = MathHelper.clamp((mouseX - (this.x + 4)) / (double) (this.width - 8), 0.0, 1.0);
        applyValue();
        updateMessage();
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return 0;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            setFromMouse(mouseX);
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        if (this.dragging) {
            setFromMouse(mouseX);
        }
        mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int knobX = this.x + (int) (this.value * (this.width - 8));
        this.drawTexturedModalRect(knobX, this.y, 0, 66, 4, 20);
        this.drawTexturedModalRect(knobX + 4, this.y, 196, 66, 4, 20);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
    }

    private static double toFraction(int value, int min, int max) {
        return max == min ? 0.0 : MathHelper.clamp((value - min) / (double) (max - min), 0.0, 1.0);
    }
}
