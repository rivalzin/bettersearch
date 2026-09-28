package com.rivalzin.bettersearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;

import java.util.function.BooleanSupplier;

public final class CategoryButton extends GuiButton implements Pressable {
    public static final int HEIGHT = 20;

    private final BooleanSupplier expanded;
    private final Runnable onToggle;

    public CategoryButton(int x, int y, int width, String label, BooleanSupplier expanded, Runnable onToggle) {
        super(0, x, y, width, HEIGHT, label);
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress() {
        onToggle.run();
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        boolean about = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        boolean active = expanded.getAsBoolean();
        int x = this.xPosition;
        int y = this.yPosition;
        int right = x + this.width;
        int bottom = y + this.height;

        int background = about ? Theme.ROW_BG_HOVER : Theme.ROW_BG;
        Gui.drawRect(x, y, right, bottom, Theme.BORDER);
        Gui.drawRect(x + 1, y + 1, right - 1, bottom - 1, background);
        mc.fontRenderer.drawStringWithShadow(active ? "-" : "+", x + 7, y + (this.height - 8) / 2, Theme.ACCENT);
        String label = this.displayString;
        int limit = Math.max(0, this.width - 28);
        if (mc.fontRenderer.getStringWidth(label) > limit) {
            label = mc.fontRenderer.trimStringToWidth(label, Math.max(0, limit - mc.fontRenderer.getStringWidth(".."))) + "..";
        }
        mc.fontRenderer.drawStringWithShadow(label, x + 21, y + (this.height - 8) / 2,
                about ? Theme.TITLE : Theme.TEXT);
    }
}
