package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {

        if (this.active && this.visible && event.isSelection()) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            onPress.run();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean highlight = (isHovered() || isFocused()) && this.active;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        guiGraphics.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.button", getMessage()));
    }
}
