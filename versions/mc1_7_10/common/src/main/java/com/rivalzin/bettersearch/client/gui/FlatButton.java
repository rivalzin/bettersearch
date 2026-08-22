package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

// no vanilla button texture: this one is flat so it looks the same on every version
public final class FlatButton extends GuiButton implements Pressable {
    private static final int BACKGROUND = 0x66000000;
    private static final int BACKGROUND_HOVER = 0xAA000000;

    private final Runnable onPress;

    public FlatButton(int x, int y, int width, int height, String label, Runnable onPress) {
        super(0, x, y, width, height, label);
        this.onPress = onPress;
    }

    public static int widthFor(String label) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(label) + 16;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        boolean about = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        boolean highlight = about && this.enabled;
        Gui.drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height,
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        this.drawCenteredString(mc.fontRenderer, this.displayString,
                this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }
}
