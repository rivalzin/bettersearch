package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import net.minecraft.network.chat.Component;

// value is stepped, the vanilla slider is 0..1 doubles
public final class IntSlider extends AbstractSliderButton {
    private final int min;
    private final int max;
    private final int step;
    private final IntFunction<Component> valueLabel;
    private final IntConsumer onChange;

    public IntSlider(int x, int y, int width, int height,
                     int min, int max, int step, int initialValue,
                     IntFunction<Component> valueLabel, IntConsumer onChange) {
        super(x, y, width, height, CommonComponents.EMPTY, toFraction(initialValue, min, max));
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.valueLabel = valueLabel;
        this.onChange = onChange;
        updateMessage();
    }

    public int intValue() {
        int raw = min + (int) Math.round(this.value * (max - min));
        int snapped = min + Math.round((raw - min) / (float) step) * step;
        return Mth.clamp(snapped, min, max);
    }

    @Override
    protected void updateMessage() {
        setMessage(valueLabel.apply(intValue()));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }

    private static double toFraction(int value, int min, int max) {
        return max == min ? 0.0 : Mth.clamp((value - min) / (double) (max - min), 0.0, 1.0);
    }
}
