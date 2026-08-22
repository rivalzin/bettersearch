package com.rivalzin.bettersearch.forge;

import com.rivalzin.bettersearch.client.CreativeSearch;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;

public final class IndexWarmer {
    private final boolean hasNei = cpw.mods.fml.common.Loader.isModLoaded("NotEnoughItems");
    private java.lang.reflect.Method installNei;
    private boolean neiFailed;

    @SubscribeEvent
    // big packs need a couple of seconds, warm it up before the first keystroke
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        installNeiHook();
        if (Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        CreativeSearch.warmUp();
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
