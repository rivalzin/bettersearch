package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import java.util.function.BooleanSupplier;

// active tab is drawn last so its border wins the overlap
public final class TabButton extends GuiButton implements Pressable {
    public static final int HEIGHT = 20;

    private final BooleanSupplier selected;
    private final Runnable onSelect;

    public TabButton(int x, int y, int width, String label, BooleanSupplier selected, Runnable onSelect) {
        super(0, x, y, width, HEIGHT, label);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onPress() {
        onSelect.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        boolean about = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        boolean active = selected.getAsBoolean();
        int x = this.xPosition;
        int y = this.yPosition;
        int right = x + this.width;
        int bottom = y + this.height;

        int background = active ? Theme.TAB_ACTIVE : (about ? Theme.TAB_HOVER : Theme.TAB_IDLE);
        Gui.drawRect(x, y, right, bottom, Theme.BORDER);
        Gui.drawRect(x + 1, y + 1, right - 1, bottom - 1, background);
        if (active) {
            Gui.drawRect(x + 1, bottom - 2, right - 1, bottom - 1, Theme.ACCENT);
        }

        String label = this.displayString;
        int limit = this.width - 6;
        if (mc.fontRenderer.getStringWidth(label) > limit) {
            label = mc.fontRenderer.trimStringToWidth(label, limit - mc.fontRenderer.getStringWidth("..")) + "..";
        }
        this.drawCenteredString(mc.fontRenderer, label,
                x + this.width / 2, y + (this.height - 8) / 2,
                active ? Theme.TITLE : Theme.TEXT_DIM);
    }
}
