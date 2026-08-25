package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

// flat tab with its own border: the accent line under it is what marks the active one
public final class TabButton extends AbstractWidget {
    public static final int HEIGHT = 20;

    private final BooleanSupplier selected;
    private final Runnable onSelect;

    public TabButton(int x, int y, int width, Component label, BooleanSupplier selected, Runnable onSelect) {
        super(x, y, width, HEIGHT, label);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        onSelect.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean active = selected.getAsBoolean();
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();

        int background = active ? Theme.TAB_ACTIVE : (isHovered() ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        guiGraphics.fill(x, y, right, bottom, Theme.BORDER);
        guiGraphics.fill(x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            guiGraphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        Minecraft minecraft = Minecraft.getInstance();
        String label = getMessage().getString();
        int limit = getWidth() - 6;
        if (minecraft.font.width(label) > limit) {
            label = minecraft.font.plainSubstrByWidth(label, limit - minecraft.font.width("..")) + "..";
        }
        guiGraphics.centeredText(minecraft.font, label,
                x + getWidth() / 2, y + (getHeight() - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.tab", getMessage()));
    }
}
