package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.CreativeSearch;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

public final class IndexWarmer {
    private final boolean hasNei = cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems");
    private java.lang.reflect.Method installNei;
    private boolean neiFailed;
    // NEI keeps the filtered list until the box changes, so a settings change has to ask it
    // again or the panel keeps showing the answer from before
    private int appliedStamp = -1;
    private boolean pokeFailed;

    @SubscribeEvent
    // big packs need a couple of seconds, warm it up before the first keystroke
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        installNeiHook();
        askNeiAgainIfSettingsChanged();
        if (Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        CreativeSearch.warmUp();
    }

    private void askNeiAgainIfSettingsChanged() {
        if (!hasNei || pokeFailed) {
            return;
        }
        int stamp = com.rivalzin.bettersearch.client.ModConfig.stamp();
        if (stamp == appliedStamp) {
            return;
        }
        boolean first = appliedStamp < 0;
        appliedStamp = stamp;
        if (first) {
            return;
        }
        try {
            Object task = Class.forName("codechicken.nei.ItemList").getField("updateFilter").get(null);
            task.getClass().getMethod("restart").invoke(task);
        } catch (Throwable t) {
            pokeFailed = true;
            com.rivalzin.bettersearch.BetterSearch.LOGGER.debug(
                    "[{}] could not ask NEI to filter again: {}",
                    com.rivalzin.bettersearch.BetterSearch.MOD_NAME, t.toString());
        }
    }

    private void installNeiHook() {
        if (!hasNei || neiFailed) {
            return;
        }
        try {
            if (installNei == null) {
                installNei = Class.forName("com.rivalzin.bettersearch.forge.nei.NeiIntegration")
                        .getMethod("install");
            }
            installNei.invoke(null);
        } catch (Throwable t) {
            neiFailed = true;
            com.rivalzin.bettersearch.BetterSearch.LOGGER.warn(
                    "[{}] could not hook NEI: {}",
                    com.rivalzin.bettersearch.BetterSearch.MOD_NAME, t.toString());
        }
    }
}
