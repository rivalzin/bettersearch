package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class CategoryButton extends AbstractWidget {
    public static final int HEIGHT = 20;

    private final BooleanSupplier expanded;
    private final Runnable onToggle;

    public CategoryButton(int x, int y, int width, Component label, BooleanSupplier expanded, Runnable onToggle) {
        super(x, y, width, HEIGHT, label);
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.active && this.visible && this.isFocused() && (keyCode == 257 || keyCode == 335 || keyCode == 32)) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            onToggle.run();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onToggle.run();
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        boolean active = expanded.getAsBoolean();
        int x = this.x;
        int y = this.y;
        int right = x + getWidth();
        int bottom = y + getHeight();

        boolean highlighted = this.isHovered || this.isFocused();
        int background = highlighted ? Theme.ROW_BG_HOVER : Theme.ROW_BG;
        GuiComponent.fill(poseStack, x, y, right, bottom, highlighted ? Theme.ACCENT : Theme.BORDER);
        GuiComponent.fill(poseStack, x + 1, y + 1, right - 1, bottom - 1, background);
        Minecraft minecraft = Minecraft.getInstance();
        GuiComponent.drawString(poseStack, minecraft.font, active ? "-" : "+", x + 7, y + (getHeight() - 8) / 2, Theme.ACCENT);
        String label = getMessage().getString();
        int limit = Math.max(0, getWidth() - 28);
        if (minecraft.font.width(label) > limit) {
            label = minecraft.font.plainSubstrByWidth(label, Math.max(0, limit - minecraft.font.width(".."))) + "..";
        }
        GuiComponent.drawString(poseStack, minecraft.font, label, x + 21, y + (getHeight() - 8) / 2,
                highlighted ? Theme.TITLE : Theme.TEXT);
    }

    public void restoreFocus() {
        this.setFocused(true);
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.empty().append(getMessage()).append(": ").append(Component.translatable(
                "bettersearch.config.category." + (expanded.getAsBoolean() ? "expanded" : "collapsed"))));
    }
}
