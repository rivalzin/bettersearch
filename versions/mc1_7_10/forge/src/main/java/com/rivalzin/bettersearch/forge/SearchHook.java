package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.BetterSearch;
import com.rivalzin.bettersearch.client.SearchableCreativeScreen;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraftforge.client.event.GuiOpenEvent;

public final class SearchHook {
    private boolean warned;

    @SubscribeEvent
    public void onScreenOpen(GuiOpenEvent event) {
        if (event.gui == null || event.gui.getClass() != GuiContainerCreative.class) {
            return;
        }
        if (Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        event.gui = new SearchableCreativeScreen(Minecraft.getMinecraft().thePlayer);
        if (!warned) {
            warned = true;
            BetterSearch.LOGGER.info("[{}] creative search hooked (screen swap on GuiOpenEvent)",
                    BetterSearch.MOD_NAME);
        }
    }
}
