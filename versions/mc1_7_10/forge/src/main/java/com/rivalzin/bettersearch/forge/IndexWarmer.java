package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.CreativeSearch;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

public final class IndexWarmer {
    private final boolean hasNei = cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems");
    private java.lang.reflect.Method installNei;
    private boolean neiFailed;

    private int appliedStamp = -1;
    private int appliedGeneration = -1;
    private boolean pokeFailed;

    @SubscribeEvent

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

        int generation = com.rivalzin.bettersearch.client.CreativeSearch.generation();
        if (stamp == appliedStamp && generation == appliedGeneration) {
            return;
        }
        boolean first = appliedStamp < 0;
        appliedStamp = stamp;
        appliedGeneration = generation;
        if (first) {
            return;
        }
        try {
            Object task = Class.forName("codechicken.nei.ItemList").getField("updateFilter").get(null);
            task.getClass().getMethod("restart").invoke(task);
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
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
        } catch (Exception | LinkageError t) {
            com.rivalzin.bettersearch.FailurePolicy.rethrowFatal(t);
            neiFailed = true;
            com.rivalzin.bettersearch.BetterSearch.LOGGER.warn(
                    "[{}] could not hook NEI: {}",
                    com.rivalzin.bettersearch.BetterSearch.MOD_NAME, t.toString());
        }
    }
}
