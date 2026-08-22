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
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;

        boolean highlight = this.hovered && this.enabled;
        Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height,
                highlight ? BACKGROUND_HOVER : BACKGROUND);
        this.drawCenteredString(mc.fontRenderer, this.displayString,
                this.x + this.width / 2, this.y + (this.height - 8) / 2,
                highlight ? Theme.ACCENT : Theme.TEXT);
    }
}
