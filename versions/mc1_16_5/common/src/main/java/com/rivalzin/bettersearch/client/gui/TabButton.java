package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.MutableComponent;
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
    @SuppressWarnings("deprecation")
    public void onClick(double mouseX, double mouseY) {
        onSelect.run();
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean active = selected.getAsBoolean();
        int x = this.x;
        int y = this.y;
        int right = x + getWidth();
        int bottom = y + getHeight();

        int background = active ? Theme.TAB_ACTIVE : (this.isHovered ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        GuiComponent.fill(poseStack, x, y, right, bottom, Theme.BORDER);
        GuiComponent.fill(poseStack, x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            GuiComponent.fill(poseStack, x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        Minecraft minecraft = Minecraft.getInstance();
        String label = getMessage().getString();
        int limit = getWidth() - 6;
        if (minecraft.font.width(label) > limit) {
            label = minecraft.font.plainSubstrByWidth(label, limit - minecraft.font.width("..")) + "..";
        }
        GuiComponent.drawCenteredString(poseStack, minecraft.font, label,
                x + getWidth() / 2, y + (getHeight() - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return (MutableComponent) ComponentCompat.translatable("gui.narrate.tab", getMessage());
    }
}
