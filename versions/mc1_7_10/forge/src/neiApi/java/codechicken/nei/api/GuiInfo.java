package codechicken.nei.api;

import java.util.HashSet;

import net.minecraft.client.gui.inventory.GuiContainer;

public class GuiInfo {
    public static HashSet<Class<? extends GuiContainer>> customSlotGuis;

    private GuiInfo() {
    }
}
