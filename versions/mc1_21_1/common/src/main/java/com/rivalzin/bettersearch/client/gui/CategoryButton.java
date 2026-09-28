package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class CategoryButton extends AbstractWidget {
    private final BooleanSupplier expanded;
    private final Runnable onPress;

    public CategoryButton(int x, int y, int width, int height, Component label,
                          BooleanSupplier expanded, Runnable onPress) {
        super(x, y, width, height, label);
        this.expanded = expanded;
        this.onPress = onPress;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (this.active && this.visible && (keyCode == 257 || keyCode == 335 || keyCode == 32)) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            onPress.run();
            return true;
        }
        return false;
    }

    @Override

    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean highlight = (isHovered() || isFocused()) && this.active;
        int x = getX();
        int y = getY();
        guiGraphics.fill(x, y, x + getWidth(), y + getHeight(),
                highlight ? Theme.ROW_BG_HOVER : Theme.ROW_BG);
        guiGraphics.fill(x, y, x + 2, y + getHeight(), Theme.ACCENT);
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int available = Math.max(0, getWidth() - 28);
        if (font.width(label) > available) {
            label = available < font.width("...") ? ""
                    : font.plainSubstrByWidth(label, available - font.width("...")) + "...";
        }
        int textY = y + (getHeight() - 8) / 2;
        guiGraphics.drawString(font, expanded.getAsBoolean() ? "-" : "+", x + 7, textY, Theme.ACCENT);
        guiGraphics.drawString(font, label, x + 20, textY, highlight ? Theme.ACCENT : Theme.TITLE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        Component state = Component.translatable("bettersearch.config.category."
                + (expanded.getAsBoolean() ? "expanded" : "collapsed"));
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button",
                Component.empty().append(getMessage()).append(": ").append(state)));
    }
}
