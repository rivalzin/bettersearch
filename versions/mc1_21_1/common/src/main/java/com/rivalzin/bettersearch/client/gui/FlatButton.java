package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

// no vanilla button texture: this one is flat so it looks the same on every version
public final class FlatButton extends AbstractWidget {
    private static final int BACKGROUND = 0x66000000;
    private static final int BACKGROUND_HOVER = 0xAA000000;

    private final Runnable onPress;

    public FlatButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    public static int widthFor(Component label) {
        return Minecraft.getInstance().font.width(label) + 16;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean highlight = isHovered() && this.active;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", getMessage()));
    }
}
